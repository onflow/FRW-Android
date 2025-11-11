package com.flowfoundation.wallet.firebase.auth

import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.flowfoundation.wallet.firebase.messaging.getFirebaseMessagingToken
import com.flowfoundation.wallet.network.clearUserCache
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.uiScope
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val TAG = "FirebaseAuth"

typealias FirebaseAuthCallback = (isSuccessful: Boolean, exception: Exception?) -> Unit

fun isAnonymousSignIn(): Boolean {
    return Firebase.auth.currentUser?.isAnonymous ?: true
}


fun isUserSignIn(): Boolean {
    val user = Firebase.auth.currentUser
    return user != null && user.uid.isNotEmpty() && isAnonymousSignIn().not()
}

fun firebaseCustomLogin(token: String, onComplete: FirebaseAuthCallback) {
    logd(TAG, "=== firebaseCustomLogin START ===")
    val auth = Firebase.auth
    val currentUser = auth.currentUser
    logd(TAG, "Current Firebase user: ${currentUser?.uid ?: "null"}")

    if (currentUser != null) {
        logd(TAG, "User already signed in, UID: ${currentUser.uid}, isAnonymous: ${currentUser.isAnonymous}")
        onComplete.invoke(true, null)
        return
    }

    logd(TAG, "Attempting to sign in with custom token (length: ${token.length})")
    auth.signInWithCustomToken(token).addOnCompleteListener { task ->
        logd(TAG, "signInWithCustomToken completed - success: ${task.isSuccessful}")
        if (!task.isSuccessful) {
            logd(TAG, "ERROR: signInWithCustomToken failed - ${task.exception?.message}")
        }

        ioScope {
            clearUserCache()
            if (task.isSuccessful) {
                val newUser = auth.currentUser
                logd(TAG, "Sign in successful, new user UID: ${newUser?.uid}")
                logd(TAG, "Requesting ID token refresh")

                newUser?.getIdToken(true)?.addOnSuccessListener { result ->
                    logd(TAG, "ID token obtained successfully")
                    uiScope {
                        onComplete.invoke(true, null)
                    }
                    getFirebaseMessagingToken()
                }?.addOnFailureListener { e ->
                    logd(TAG, "ERROR: Failed to get ID token - ${e.message}")
                    uiScope { onComplete.invoke(false, e) }
                }
            } else {
                logd(TAG, "ERROR: Task unsuccessful, calling failure callback")
                val exception = task.exception
                logd(TAG, "Exception type: ${exception?.javaClass?.simpleName}")
                logd(TAG, "Exception message: ${exception?.message}")
                uiScope {
                    onComplete.invoke(false, exception)
                }
            }
        }
    }
}

fun firebaseUid() = Firebase.auth.currentUser?.uid

suspend fun getFirebaseJwt(forceRefresh: Boolean = false) = suspendCoroutine { continuation ->
    val auth = Firebase.auth
    val user = auth.currentUser
    
    if (user != null) {
        // User exists, get ID token from existing user
        logd(TAG, "User exists, getting ID token: ${user.uid}, isAnonymous: ${user.isAnonymous}")
        user.getIdToken(forceRefresh).addOnCompleteListener { task ->
            if (task.isSuccessful && task.result != null) {
                val token = task.result.token
                if (token.isNullOrEmpty()) {
                    loge(TAG, "ID token is null or empty")
                    continuation.resume("")
                } else {
                    logd(TAG, "ID token obtained successfully (length: ${token.length})")
                    continuation.resume(token)
                }
            } else {
                val exception = task.exception
                val errorMessage = exception?.message ?: "Unknown error"
                loge(TAG, "Failed to get ID token: $errorMessage")
                
                // Check if it's a network error
                if (errorMessage.contains("network", ignoreCase = true) || 
                    errorMessage.contains("unreachable", ignoreCase = true) ||
                    errorMessage.contains("timeout", ignoreCase = true)) {
                    loge(TAG, "Network error detected - Firebase services may be unreachable")
                }
                
                continuation.resume("")
            }
        }
    } else {
        // No user exists, sign in anonymously first (matching extension behavior)
        logd(TAG, "No Firebase user found, signing in anonymously...")
        auth.signInAnonymously().addOnCompleteListener { signInTask ->
            if (!signInTask.isSuccessful) {
                val exception = signInTask.exception
                val errorMessage = exception?.message ?: "Unknown error"
                loge(TAG, "Failed to sign in anonymously: $errorMessage")
                
                // Check if it's a network error
                val isNetworkError = errorMessage.contains("network", ignoreCase = true) || 
                    errorMessage.contains("unreachable", ignoreCase = true) ||
                    errorMessage.contains("timeout", ignoreCase = true) ||
                    errorMessage.contains("No address associated", ignoreCase = true) ||
                    errorMessage.contains("Unable to resolve host", ignoreCase = true) ||
                    exception?.javaClass?.simpleName?.contains("Network", ignoreCase = true) == true
                
                if (isNetworkError) {
                    val detailedError = "Network error preventing anonymous sign-in - Firebase services unreachable. " +
                        "Error: $errorMessage. " +
                        "Troubleshooting: " +
                        "1. Check device/emulator internet connectivity " +
                        "2. Verify Firebase configuration (google-services.json) " +
                        "3. For emulators, ensure DNS is configured (use 8.8.8.8) " +
                        "4. Check firewall/proxy settings"
                    loge(TAG, detailedError)
                }
                
                continuation.resume("")
                return@addOnCompleteListener
            }
            
            // Get user from task result (more reliable than auth.currentUser)
            val anonymousUser = signInTask.result?.user ?: auth.currentUser
            if (anonymousUser == null) {
                loge(TAG, "Sign in succeeded but user is null")
                continuation.resume("")
                return@addOnCompleteListener
            }
            
            logd(TAG, "Anonymous sign-in successful, user: ${anonymousUser.uid}")
            anonymousUser.getIdToken(forceRefresh).addOnCompleteListener { tokenTask ->
                if (tokenTask.isSuccessful && tokenTask.result != null) {
                    val token = tokenTask.result.token
                    if (token.isNullOrEmpty()) {
                        loge(TAG, "Anonymous user ID token is null or empty")
                        continuation.resume("")
                    } else {
                        logd(TAG, "Anonymous user ID token obtained successfully (length: ${token.length})")
                        continuation.resume(token)
                    }
                } else {
                    val exception = tokenTask.exception
                    val errorMessage = exception?.message ?: "Unknown error"
                    loge(TAG, "Failed to get ID token from anonymous user: $errorMessage")
                    
                    // Check if it's a network error
                    if (errorMessage.contains("network", ignoreCase = true) || 
                        errorMessage.contains("unreachable", ignoreCase = true) ||
                        errorMessage.contains("timeout", ignoreCase = true)) {
                        loge(TAG, "Network error preventing token retrieval - Firebase services unreachable")
                    }
                    
                    continuation.resume("")
                }
            }
        }
    }
}

suspend fun deleteAnonymousUser() = suspendCoroutine { continuation ->
    FirebaseMessaging.getInstance().deleteToken()
    Firebase.auth.currentUser?.delete()?.addOnCompleteListener { task ->
        logd(TAG, "delete anonymous user finish , exception:${task.exception}")
        continuation.resume(task.isSuccessful)
    }
}

suspend fun signInAnonymously() = suspendCoroutine { continuation ->
    Firebase.auth.signInAnonymously().addOnCompleteListener { signInTask ->
        continuation.resume(signInTask.isSuccessful)
    }
}