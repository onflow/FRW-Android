package com.flowfoundation.wallet.manager.notification

import com.flowfoundation.wallet.page.notification.model.WalletNotification
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList


object WalletNotificationManager {

    private val TAG = WalletNotificationManager::class.java.simpleName
    private val listeners = CopyOnWriteArrayList<WeakReference<OnNotificationUpdate>>()
    private val notificationList = mutableListOf<WalletNotification>()

    fun getNotificationList(): List<WalletNotification> {
        return notificationList.toList()
    }

    fun alreadyExist(title: String): Boolean {
        return notificationList.find { it.title == title } != null
    }

    fun haveNotification(): Boolean {
        return notificationList.isNotEmpty()
    }

    fun addNotification(notification: WalletNotification) {
        notificationList.add(0, notification)
        dispatchListeners()
    }

    fun removeNotification(notification: WalletNotification) {
        notificationList.remove(notification)
        dispatchListeners()
    }

    fun clear() {
        if (notificationList.isNotEmpty()) {
            notificationList.clear()
            dispatchListeners()
        }
    }

    fun addListener(callback: OnNotificationUpdate) {
        if (listeners.firstOrNull { it.get() == callback } != null) {
            return
        }
        uiScope {
            this.listeners.add(WeakReference(callback))
        }
    }

    private fun dispatchListeners() {
        logd(TAG, "dispatchListeners notificationUpdate")
        uiScope {
            listeners.removeAll { it.get() == null }
            listeners.forEach { it.get()?.onNotificationUpdate() }
        }
    }
}

interface OnNotificationUpdate {
    fun onNotificationUpdate()
}