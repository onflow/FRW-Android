package com.flowfoundation.wallet.page.walletrestore.model

import com.google.gson.annotations.SerializedName

data class WalletRestoreContentModel(
    @SerializedName("changeStep")
    val changeStep: Pair<Int, Any?>? = null,
)