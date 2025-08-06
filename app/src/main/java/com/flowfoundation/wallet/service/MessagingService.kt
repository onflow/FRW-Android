package com.flowfoundation.wallet.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.flowfoundation.wallet.firebase.messaging.getFirebaseMessagingToken
import com.flowfoundation.wallet.firebase.messaging.parseFirebaseMessaging
import com.flowfoundation.wallet.firebase.messaging.uploadPushToken
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.updatePushToken
import com.instabug.chat.Replies

class MessagingService : FirebaseMessagingService() {

    override fun onCreate() {
        super.onCreate()
        logd(TAG, "onCreate()")
        // subscribeMessagingTopic("test")
        getFirebaseMessagingToken()
    }

    override fun onNewToken(token: String) {
        ioScope {
            updatePushToken(token)
            uploadPushToken(isNewToken = true)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        logd(TAG, "receive new firebase message:$message")
        parseFirebaseMessaging(message)
        if (Replies.isInstabugNotification(message.getData())) {
            Replies.showNotification(message.getData())
        }
    }

    /**
     * 在某些情况下，FCM 可能不会传递消息。如果在特定设备连接 FCM 时，您的应用在该设备上的待处理消息过多（超过 100 条），
     * 或者设备超过一个月未连接到 FCM，就会发生这种情况。在这些情况下，
     * 您可能会收到对 FirebaseMessagingService.onDeletedMessages() 的回调。
     * 当应用实例收到此回调时，应该执行一次与应用服务器的完全同步。
     * 如果您在过去 4 周内未向该设备上的应用发送消息，FCM 将不会调用 onDeletedMessages()。
     */
    override fun onDeletedMessages() {

    }

    companion object {
        private val TAG = MessagingService::class.java.simpleName
    }
}