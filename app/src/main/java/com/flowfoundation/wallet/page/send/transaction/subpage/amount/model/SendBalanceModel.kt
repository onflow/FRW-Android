package com.flowfoundation.wallet.page.send.transaction.subpage.amount.model

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class SendBalanceModel(
    @SerializedName("contractId")
    val contractId: String,
    @SerializedName("balance")
    val balance: BigDecimal = BigDecimal.ZERO,
    @SerializedName("coinRate")
    val coinRate: BigDecimal = BigDecimal.ZERO,
)