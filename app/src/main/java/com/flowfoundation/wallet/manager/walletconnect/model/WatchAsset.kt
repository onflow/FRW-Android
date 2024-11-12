package com.flowfoundation.wallet.manager.walletconnect.model

import com.google.gson.annotations.SerializedName

data class WatchAsset(
    @SerializedName("type")
    val type: String,
    @SerializedName("options")
    val options: Options?
) {
    data class Options(
        @SerializedName("address")
        val address: String?,
        @SerializedName("symbol")
        val symbol: String?,
        @SerializedName("decimals")
        val decimals: Int?,
        @SerializedName("image")
        val image: String?
    )
}