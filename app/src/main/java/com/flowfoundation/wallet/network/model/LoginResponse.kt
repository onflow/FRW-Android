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
    val customToken: String? = null,

    @SerializedName("user_id")
    val uid: String? = null,

    @SerializedName("id")
    val id: String? = null,

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