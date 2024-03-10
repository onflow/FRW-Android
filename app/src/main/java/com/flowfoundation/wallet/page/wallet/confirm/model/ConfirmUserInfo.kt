package com.flowfoundation.wallet.page.wallet.confirm.model

import com.google.gson.annotations.SerializedName


data class ConfirmUserInfo(
    @SerializedName("user_avatar")
    val userAvatar: String,
    @SerializedName("user_name")
    val userName: String,
    @SerializedName("address")
    val address: String,
)