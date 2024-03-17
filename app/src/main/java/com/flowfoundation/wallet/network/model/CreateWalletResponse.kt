package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName

data class CreateWalletResponse(

    @SerializedName("data")
    val data: CreateWalletResponseData,

    @SerializedName("message")
    val message: String,

    @SerializedName("status")
    val status: Int,
)

data class CreateWalletResponseData(
    @SerializedName("id")
    val id: String,

    @SerializedName("primary_wallet")
    val primaryWallet: Int,

    @SerializedName("username")
    val username: String,

    @SerializedName("created_at")
    val createdAt: String,

    @SerializedName("updated_at")
    val updatedAt: String,
)