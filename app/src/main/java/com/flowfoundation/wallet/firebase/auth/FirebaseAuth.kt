package com.flowfoundation.wallet.firebase.auth

import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.flowfoundation.wallet.firebase.messaging.getFirebaseMessagingToken
import com.flowfoundation.wallet.network.clearUserCache
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
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
    ioScope {
        val auth = Firebase.auth
        if (auth.currentUser == null) {
            signInAnonymously()
        }

        val user = auth.currentUser
        if (user == null) {
            continuation.resume("")
            return@ioScope
        }

        user.getIdToken(forceRefresh).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result.token.orEmpty())
            } else {
                continuation.resume("")
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