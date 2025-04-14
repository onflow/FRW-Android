package com.flowfoundation.wallet.page.notification.model

import com.flowfoundation.wallet.manager.account.AccountInfoManager
import com.flowfoundation.wallet.manager.config.AppConfig.isVersionUpdateRequired
import com.flowfoundation.wallet.utils.svgToPng
import com.google.gson.annotations.SerializedName
import java.util.Date


data class WalletNotification(
    @SerializedName("id")
    val id: String,
    @SerializedName("priority")
    val priority: Priority,
    @SerializedName("type")
    val type: Type,
    @SerializedName("title")
    val title: String? = null,
    @SerializedName("body")
    val body: String? = null,
    @SerializedName("icon")
    val icon: String? = null,
    @SerializedName("image")
    val image: String? = null,
    @SerializedName("url")
    val url: String? = null,
    @SerializedName("expiry_time")
    val expiryTime: Date,
    @SerializedName("display_type")
    val displayType: DisplayType,
    @SerializedName("conditions")
    val conditions: List<Condition>?
) {
    fun icon(): String? {
        return if (icon?.endsWith(".svg") == true) {
            icon.svgToPng()
        } else {
            icon
        }
    }

    fun isExpired(): Boolean {
        if (displayType == DisplayType.EXPIRY) {
            val currentTime = Date()
            return currentTime.after(expiryTime)
        } else {
            return false
        }
    }

    fun isConditionMet(): Boolean {
        return conditions?.all { condition ->
            when (condition.type) {
                ConditionType.CAN_UPGRADE -> isVersionUpdateRequired()
                ConditionType.INSUFFICIENT_STORAGE -> AccountInfoManager.isStorageInsufficient()
                ConditionType.INSUFFICIENT_BALANCE -> AccountInfoManager.isBalanceInsufficient()
                ConditionType.IS_ANDROID -> true
                ConditionType.UNKNOWN -> false
            }
        } ?: true
    }
}

data class Condition(
    @SerializedName("type")
    val type: ConditionType,
)


enum class Type {
    @SerializedName("image")
    IMAGE,

    @SerializedName("pending_request")
    PENDING_REQUEST
}

enum class Priority {
    @SerializedName("urgent")
    URGENT,
    @SerializedName("low")
    LOW
}

enum class DisplayType {
    @SerializedName("once")
    ONCE,    // 只显示一次

    @SerializedName("click")
    CLICK,   // 用户点击，或者关闭后，不再显示

    @SerializedName("expiry")
    EXPIRY   // 一直显示直到过期，用户关闭后，下次启动再显示
}

enum class ConditionType {
    @SerializedName("canUpgrade")
    CAN_UPGRADE,
    @SerializedName("isAndroid")
    IS_ANDROID,
    @SerializedName("insufficientStorage")
    INSUFFICIENT_STORAGE,
    @SerializedName("insufficientBalance")
    INSUFFICIENT_BALANCE,
    @SerializedName("unknown")
    UNKNOWN
}
