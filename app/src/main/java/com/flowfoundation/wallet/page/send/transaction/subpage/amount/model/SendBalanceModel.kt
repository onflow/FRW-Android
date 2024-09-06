package com.flowfoundation.wallet.page.send.transaction.subpage.amount.model

import com.google.gson.annotations.SerializedName

data class SendBalanceModel(
    @SerializedName("symbol")
    val symbol: String,
    @SerializedName("balance")
    val balance: Float = 0.0f,
    @SerializedName("coinRate")
    val coinRate: Float = 0.0f,
)