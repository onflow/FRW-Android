package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName

data class NetworkEnableResponse(
    @SerializedName("data")
    val transactionId: String? = null,

    @SerializedName("message")
    val message: String,

    @SerializedName("status")
    val status: Int,
)

data class NetworkEnableParams(
    @SerializedName("account_key")
    val accountKey: AccountKey,
    @SerializedName("network")
    val network: String
)