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
    val customToken: String? = null,

    @SerializedName("id")
    val uid: String,

    // FRW-Server session tokens (auth-contract.md). custom_token is legacy and omitted once
    // the Firebase fallback is off server-side.
    @SerializedName("access_token")
    val accessToken: String? = null,

    @SerializedName("refresh_token")
    val refreshToken: String? = null,

    @SerializedName("expires_in")
    val expiresIn: Long? = null,

    @SerializedName("refresh_expires_in")
    val refreshExpiresIn: Long? = null,
)