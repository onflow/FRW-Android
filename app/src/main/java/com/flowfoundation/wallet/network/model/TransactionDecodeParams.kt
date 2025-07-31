package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName


data class TransactionDecodeParams(
    @SerializedName("to")
    val to: String,
    @SerializedName("data")
    val data: String
)
