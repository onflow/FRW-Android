package com.flowfoundation.wallet.page.profile.subpage.developer.model

import com.google.gson.annotations.SerializedName

data class LocalAccountKey(
    @SerializedName("userId")
    val userId: String,
    @SerializedName("userName")
    val userName: String,
    @SerializedName("publicKey")
    val publicKey: String
)
