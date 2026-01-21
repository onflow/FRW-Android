package com.flowfoundation.wallet.page.transaction

import org.onflow.flow.models.TransactionStatus

// TransactionRecord conversion removed - Activity screen now uses React Native

fun Int.transactionStateToString(): String {
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