package com.flowfoundation.wallet.page.walletcreate.fragments.username

import com.google.gson.annotations.SerializedName

data class WalletCreateUsernameModel(
    @SerializedName("username")
    val username: String? = null,
    @SerializedName("state")
    val state: Pair<Boolean, String>? = null,
    @SerializedName("createUserSuccess")
    val createUserSuccess: Boolean? = null,
)