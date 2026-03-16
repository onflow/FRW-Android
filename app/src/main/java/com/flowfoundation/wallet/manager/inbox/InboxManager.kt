package com.flowfoundation.wallet.manager.inbox

import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryInboxUnclaimedNumber
import com.flowfoundation.wallet.manager.notification.WalletNotificationManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.notification.model.DisplayType
import com.flowfoundation.wallet.page.notification.model.Priority
import com.flowfoundation.wallet.page.notification.model.Type
import com.flowfoundation.wallet.page.notification.model.WalletNotification
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

object InboxManager {

    private const val TAG = "InboxManager"

    private val listeners = CopyOnWriteArrayList<WeakReference<OnInboxCountUpdate>>()

    @Volatile
    var unclaimedCount: Int = 0
        private set

    private var inboxNotification: WalletNotification? = null

    fun refresh() {
        ioScope {
            if (WalletManager.isChildAccountSelected() || WalletManager.isEVMAccountSelected()) {
                updateCount(0)
                return@ioScope
            }

            val address = WalletManager.selectedWalletAddress()
            if (address.isBlank()) {
                updateCount(0)
                return@ioScope
            }

            val count = cadenceQueryInboxUnclaimedNumber(listOf(address)) ?: 0
            logd(TAG, "refresh() unclaimed count: $count for $address")
            updateCount(count)
        }
    }

    fun addListener(callback: OnInboxCountUpdate) {
        if (listeners.firstOrNull { it.get() == callback } != null) {
            return
        }
        uiScope {
            listeners.add(WeakReference(callback))
        }
    }

    fun formatBadgeCount(count: Int): String {
        return if (count >= 1000) "${count / 1000}k" else count.toString()
    }

    fun clearNotificationRef() {
        inboxNotification = null
    }

    private fun updateCount(count: Int) {
        unclaimedCount = count
        manageNotification(count)
        dispatchListeners()
    }

    private fun manageNotification(count: Int) {
        if (count > 0 && inboxNotification == null) {
            val notification = WalletNotification(
                id = "inbox_${System.currentTimeMillis()}",
                icon = "https://raw.githubusercontent.com/Outblock/Assets/main/ft/flow/logo.png",
                title = "Assets available to claim",
                body = "Go to view.",
                priority = Priority.URGENT,
                type = Type.INBOX,
                displayType = DisplayType.CLICK
            )
            inboxNotification = notification
            WalletNotificationManager.addNotification(notification)
        } else if (count <= 0 && inboxNotification != null) {
            inboxNotification?.let { WalletNotificationManager.removeNotification(it) }
            inboxNotification = null
        }
    }

    private fun dispatchListeners() {
        uiScope {
            listeners.removeAll { it.get() == null }
            listeners.forEach { it.get()?.onInboxCountUpdate(unclaimedCount) }
        }
    }
}

interface OnInboxCountUpdate {
    fun onInboxCountUpdate(count: Int)
}
