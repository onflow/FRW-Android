package com.flowfoundation.wallet.manager.notification

import com.flowfoundation.wallet.page.notification.model.WalletNotification
import com.flowfoundation.wallet.utils.getNotificationReadList
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.utils.updateNotificationListPref
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.reflect.TypeToken
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList


object WalletNotificationManager {

    private val TAG = WalletNotificationManager::class.java.simpleName
    private val listeners = CopyOnWriteArrayList<WeakReference<OnNotificationUpdate>>()
    private val notificationList = mutableListOf<WalletNotification>()
    private val readList = mutableListOf<String>()

    init {
        ioScope {
            val cachedReadList = getNotificationReadList()
            val list = Gson().fromJson<List<String>>(cachedReadList, object : TypeToken<List<String>>() {}.type)
            readList.clear()
            readList.addAll(list)
        }
    }

    fun getNotificationList(): List<WalletNotification> {
        return notificationList.filterNot { readList.contains(it.id) || it.isExpired() }.toList()
    }

    fun setNotificationList(listStr: String) {
        logd(TAG, "notification::$listStr")
        val gson: Gson = GsonBuilder()
            .registerTypeAdapter(Date::class.java, JsonDeserializer { json, _, _ ->
                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.getDefault())
                dateFormat.timeZone = TimeZone.getTimeZone("UTC")
                dateFormat.parse(json.asString)
            })
            .create()
        val list = gson.fromJson<List<WalletNotification>>(listStr, object : TypeToken<List<WalletNotification>>() {}.type)
        logd(TAG, "list::$list")
        notificationList.clear()
        notificationList.addAll(list.sortedBy { it.priority })
    }

    fun alreadyExist(id: String): Boolean {
        return notificationList.find { it.id == id } != null
    }

    fun markAsRead(id: String) {
        if (readList.contains(id)) {
            return
        }
        readList.add(id)
        ioScope {
            updateNotificationListPref(Gson().toJson(readList))
        }
    }

    fun haveNotification(): Boolean {
        return getNotificationList().isNotEmpty()
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