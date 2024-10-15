package com.flowfoundation.wallet.manager.account.model

import com.google.gson.annotations.SerializedName


data class LocalSwitchAccount(
    @SerializedName("username")
    val username: String,
    @SerializedName("address")
    val address: String,
    @SerializedName("userId")
    val userId: String? = null,
    @SerializedName("prefix")
    val prefix: String? = null
)
