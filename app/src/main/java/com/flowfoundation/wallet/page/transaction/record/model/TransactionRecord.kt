package com.flowfoundation.wallet.page.transaction.record.model

import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.network.flowscan.model.FlowScanTransaction

data class TransactionRecord(
    @SerializedName("transaction")
    val transaction: FlowScanTransaction
)

