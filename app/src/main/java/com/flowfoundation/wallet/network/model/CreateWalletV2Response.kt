package com.flowfoundation.wallet.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateWalletV2Response(

    @SerialName("data")
    val data: CreateWalletV2ResponseData?,

    @SerialName("message")
    val message: String,

    @SerialName("status")
    val status: Int,
)

@Serializable
data class CreateWalletV2ResponseData(
    @SerialName("txid")
    val txid: String?,
)
