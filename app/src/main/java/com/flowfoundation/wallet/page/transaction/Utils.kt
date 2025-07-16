package com.flowfoundation.wallet.page.transaction

import com.flowfoundation.wallet.manager.transaction.TransactionState
import org.onflow.flow.models.TransactionStatus
import com.flowfoundation.wallet.network.flowscan.model.FlowScanTransaction
import com.flowfoundation.wallet.page.transaction.record.model.TransactionRecord
import org.joda.time.format.ISODateTimeFormat


fun TransactionState.toTransactionRecord(): TransactionRecord {
    return TransactionRecord(
        transaction = FlowScanTransaction(
            hash = transactionId,
            time = ISODateTimeFormat.dateTime().print(time),
            status = state.stateToString(),
            error = errorMsg,
        )
    )
}

private fun Int.stateToString(): String {
    return when (this) {
        TransactionStatus.UNKNOWN.ordinal -> "Unknown"
        TransactionStatus.PENDING.ordinal -> "Pending"
        TransactionStatus.FINALIZED.ordinal -> "Finalized"
        TransactionStatus.EXECUTED.ordinal -> "Executed"
        TransactionStatus.SEALED.ordinal -> "Sealed"
        TransactionStatus.EXPIRED.ordinal -> "Expired"
        else -> ""
    }
}