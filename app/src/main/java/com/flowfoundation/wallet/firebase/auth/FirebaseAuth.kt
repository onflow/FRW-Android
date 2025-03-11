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
    val auth = Firebase.auth
    if (auth.currentUser != null) {
        logd(TAG, "have signed in")
        onComplete.invoke(true, null)
        return
    }
    auth.signInWithCustomToken(token).addOnCompleteListener { task ->
        ioScope {
            clearUserCache()
            uiScope { onComplete.invoke(task.isSuccessful, task.exception) }
            getFirebaseMessagingToken()
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