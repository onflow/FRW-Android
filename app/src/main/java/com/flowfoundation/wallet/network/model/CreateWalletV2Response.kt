package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName

data class CreateWalletV2Response(

    @SerializedName("data")
    val data: CreateWalletV2ResponseData?,

    @SerializedName("message")
    val message: String,

    @SerializedName("status")
    val status: Int,
)

data class CreateWalletV2ResponseData(
    @SerializedName("txid")
    val txid: String?,
)
