package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName

data class SearchUserResponse(
    @SerializedName("data")
    val data: SearchUsers,

    @SerializedName("message")
    val message: String,

    @SerializedName("status")
    val status: Int,
)

data class SearchUsers(
    @SerializedName("users")
    val users: List<UserInfoData>? = null,
)
