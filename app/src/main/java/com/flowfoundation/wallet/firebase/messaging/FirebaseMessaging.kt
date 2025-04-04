package com.flowfoundation.wallet.firebase.messaging

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
import com.flowfoundation.wallet.firebase.auth.isAnonymousSignIn
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.retrofitWithHost
import com.flowfoundation.wallet.utils.getPushToken
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.isDev
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.updatePushToken

private const val TAG = "FirebaseMessaging"

fun getFirebaseMessagingToken() {
    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
        if (!task.isSuccessful) {
            loge(task.exception)
            return@addOnCompleteListener
        }
        val token = task.result
        logd(TAG, "token:$token")
        updatePushToken(token)
    }
}

fun parseFirebaseMessaging(message: RemoteMessage) {
    val data = message.data
    val body = message.notification?.body
    val title = message.notification?.title

    logd(TAG, "parseFirebaseMessaging => data:$data,title:$title,body:$body")
//    sendNotification(message)
}

fun uploadPushToken(isNewToken: Boolean = false) {
    ioScope {
        val token = getPushToken()
        val address = WalletManager.selectedWalletAddress()
        if (token.isEmpty() || isAnonymousSignIn() || address.isEmpty()) {
            return@ioScope
        }
        if (isNewToken.not() && AccountManager.isAddressUploaded(address)) {
            return@ioScope
        }
        val retrofit =
            retrofitWithHost(if (isDev()) "https://dev-scanner.lilico.app" else "https://scanner.lilico.app", ignoreAuthorization = false)
        val service = retrofit.create(ApiService::class.java)
        val params = mapOf(
            "token" to getPushToken(),
            "address" to address,
        )
        logd(TAG, "uploadPushToken => params:$params")

        val resp = service.uploadPushToken(params)

        logd(TAG, resp)
        if (resp.status == 200) {
            AccountManager.addressUploaded(address)
        }
    }
}

