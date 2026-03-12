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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "FirebaseAuth"

// Serialises all "currentUser == null → sign in" operations to prevent
// a concurrent getFirebaseJwt() from racing with setToAnonymous().
private val authStateMutex = Mutex()

/**
 * Signs out the current non-anonymous user and signs in anonymously.
 * Holds [authStateMutex] during signOut + signInAnonymously so that any
 * concurrent [getFirebaseJwt] call waits instead of submitting a second
 * signInAnonymously task that could later overwrite the custom-token user.
 */
suspend fun setToAnonymous(): Boolean {
    if (!isAnonymousSignIn()) {
        authStateMutex.withLock {
            Firebase.auth.signOut()
            signInAnonymously()
        }
        return isAnonymousSignIn()
    }
    return true
}

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
    logd(TAG, "Current Firebase user: ${auth.currentUser?.uid ?: "null"}")

    auth.signInWithCustomToken(token).addOnCompleteListener { task ->
        if (task.isSuccessful) {
            logd(TAG, "Sign in successful, new user UID: ${auth.currentUser?.uid}")
            onComplete.invoke(true, null)
            // Background cleanup — runs after the caller's callback has already returned.
            ioScope {
                clearUserCache()
                getFirebaseMessagingToken()
            }
        } else {
            logd(TAG, "ERROR: signInWithCustomToken failed - ${task.exception?.message}")
            onComplete.invoke(false, task.exception)
        }
    }
}

fun firebaseUid(): String? {
    val uid = Firebase.auth.currentUser?.uid
    logd(TAG, "firebaseUid: $uid")
    return uid
}

suspend fun getFirebaseJwt(forceRefresh: Boolean = false) = suspendCoroutine { continuation ->
    ioScope {
        val auth = Firebase.auth
        if (auth.currentUser == null) {
            authStateMutex.withLock {
                // Re-check after acquiring lock: setToAnonymous() may have already
                // completed and set currentUser while we were waiting.
                if (Firebase.auth.currentUser == null) {
                    signInAnonymously()
                }
            }
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
