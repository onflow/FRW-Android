package com.flowfoundation.wallet.page.walletcreate.fragments.username

data class WalletCreateUsernameModel(
    val username: String? = null,
    val state: Pair<Boolean, String>? = null,
    val createUserSuccess: Boolean? = null,
)