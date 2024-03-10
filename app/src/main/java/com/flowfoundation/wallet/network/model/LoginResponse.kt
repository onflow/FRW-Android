package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("data")
    val data: LoginResponseData? = null,

    @SerializedName("message")
    val message: String,

    @SerializedName("status")
    val status: Int,
)

data class LoginResponseData(
    @SerializedName("custom_token")
    val customToken: String?,

    @SerializedName("user_id")
    val uid: String?,
)