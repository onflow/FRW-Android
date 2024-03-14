package com.flowfoundation.wallet.page.send.transaction.subpage.amount.model

data class SendBalanceModel(
    val symbol: String,
    val balance: Float = 0.0f,
    val coinRate: Float = 0.0f,
)