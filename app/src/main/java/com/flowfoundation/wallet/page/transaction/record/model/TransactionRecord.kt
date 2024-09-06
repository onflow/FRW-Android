package com.flowfoundation.wallet.page.transaction.record.model

import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.network.flowscan.model.FlowScanTransaction

data class TransactionRecord(
    @SerializedName("transaction")
    val transaction: FlowScanTransaction
)

data class TransactionRecordList(
    @SerializedName("list")
    val list: List<FlowScanTransaction>
)