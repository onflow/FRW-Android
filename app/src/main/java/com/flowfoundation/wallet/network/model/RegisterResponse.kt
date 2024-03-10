package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName

data class RegisterResponse(
    @SerializedName("data")
    val data: RegisterResponseData,

    @SerializedName("message")
    val message: String,

    @SerializedName("status")
    val status: Int,
)

data class RegisterResponseData(
    @SerializedName("custom_token")
    val customToken: String,

    @SerializedName("id")
    val uid: String,
)