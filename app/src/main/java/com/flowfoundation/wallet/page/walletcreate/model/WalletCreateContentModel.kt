package com.flowfoundation.wallet.page.walletcreate.model

import com.google.gson.annotations.SerializedName

data class WalletCreateContentModel(
    @SerializedName("changeStep")
    val changeStep: Int? = null,
)