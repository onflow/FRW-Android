package io.outblock.lilico.network.model

import com.google.gson.annotations.SerializedName


data class AccountSignRequest(
    @SerializedName("account_key")
    val accountKey: AccountKey,

    @SerializedName("signatures")
    val deviceInfo: List<AccountKeySignature>,
)