package com.flowfoundation.wallet.page.notification.model

import com.flowfoundation.wallet.utils.svgToPng
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable


@Serializable
data class WalletNotification(
    @SerializedName("icon")
    val icon: String? = null,
    @SerializedName("title")
    val title: String,
    @SerializedName("content")
    val content: String,
    @SerializedName("clickable")
    val clickable: Boolean,
    @SerializedName("deep_link")
    val deepLink: String? = null,
) {
    fun icon(): String? {
        return icon?.svgToPng()
    }
}
