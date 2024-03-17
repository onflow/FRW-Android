package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName

data class SandboxEnableResponse(
    @SerializedName("data")
    val transactionId: String? = null,

    @SerializedName("message")
    val message: String,

    @SerializedName("status")
    val status: Int,
)