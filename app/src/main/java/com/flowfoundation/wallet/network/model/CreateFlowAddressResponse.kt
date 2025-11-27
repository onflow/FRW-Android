package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName

data class CreateFlowAddressResponse(
    @SerializedName("data")
    val data: CreateFlowAddressResponseData?,

    @SerializedName("message")
    val message: String,

    @SerializedName("status")
    val status: Int,
)

data class CreateFlowAddressResponseData(
    @SerializedName("txid")
    val txId: String,
)
