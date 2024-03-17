package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName

data class AddressInfoResponse(
    @SerializedName("data")
    val data: AddressInfoDataWrapper,

    @SerializedName("message")
    val message: String,

    @SerializedName("status")
    val status: Int,
)

data class AddressInfoDataWrapper(
    @SerializedName("data")
    val data: AddressInfoData,
)

data class AddressInfoData(
    @SerializedName("account")
    val account: AddressInfoAccount,
)

data class AddressInfoAccount(
    @SerializedName("address")
    val address: String,
    @SerializedName("balance")
    var balance: String,
)