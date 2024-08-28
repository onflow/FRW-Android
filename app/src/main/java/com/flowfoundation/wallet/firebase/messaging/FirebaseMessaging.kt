package com.flowfoundation.wallet.firebase.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.messaging.ktx.messaging
import com.google.gson.Gson
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.firebase.auth.isAnonymousSignIn
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.retrofitWithHost
import com.flowfoundation.wallet.page.common.NotificationDispatchActivity
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.getPushToken
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.isDev
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logw
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

fun subscribeMessagingTopic(topic: String) {
    logd(TAG, "subscribeMessagingTopic => $topic")
    Firebase.messaging.subscribeToTopic(topic)
        .addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                logw(TAG, "msg subscribe failed")
                return@addOnCompleteListener
            }
            logd(TAG, "firebase message subscribe success")
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

private fun sendNotification(message: RemoteMessage) {
    val requestCode = 0
    val intent = Intent(Env.getApp(), NotificationDispatchActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    intent.putExtra("data", Gson().toJson(message.data))
    val pendingIntent = PendingIntent.getActivity(
        Env.getApp(),
        requestCode,
        intent,
        PendingIntent.FLAG_IMMUTABLE,
    )

    val channelId = Env.getApp().getString(R.string.notification_channel_id)
    val notificationBuilder = NotificationCompat.Builder(Env.getApp(), channelId)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(message.notification?.title)
        .setContentText(message.notification?.body)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)

    if (message.notification?.defaultSound == true) {
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        notificationBuilder.setSound(defaultSoundUri)
    }

    val notificationManager = Env.getApp().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Since android Oreo notification channel is needed.
    val channel = NotificationChannel(
        channelId,
        R.string.notification_channel_name.res2String(),
        NotificationManager.IMPORTANCE_DEFAULT,
    )
    notificationManager.createNotificationChannel(channel)

    val notificationId = 0
    notificationManager.notify(notificationId, notificationBuilder.build())
}
