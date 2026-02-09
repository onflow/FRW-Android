package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName

data class CoinbaseOnRampResponse(
    @SerializedName("data")
    val data: CoinbaseOnRampInfo?,
    @SerializedName("status")
    val status: Int?
)

data class CoinbaseOnRampInfo(
    @SerializedName("session")
    val session: CoinbaseOnRampSession?
)

data class CoinbaseOnRampSession(
    @SerializedName("onrampUrl")
    val onRampUrl: String?
)
