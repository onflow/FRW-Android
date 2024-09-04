package com.flowfoundation.wallet.page.profile.subpage.walletconnect.sessiondetail.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class WalletConnectSessionDetailModel(
    @SerializedName("topic")
    val topic:String,
    @SerializedName("icon")
    val icon: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("url")
    val url: String,
    @SerializedName("expiry")
    val expiry: Long,
    @SerializedName("address")
    val address: String,
    @SerializedName("network")
    val network: String,
) : Parcelable
