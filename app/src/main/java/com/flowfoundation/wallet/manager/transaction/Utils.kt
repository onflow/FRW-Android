package com.flowfoundation.wallet.manager.transaction

import org.onflow.flow.models.TransactionResult
import org.onflow.flow.models.TransactionStatus

const val ERROR_STORAGE_CAPACITY_EXCEEDED = 1103

fun TransactionResult.isProcessing(): Boolean {
    return status!!.ordinal.isProcessing()
}

fun TransactionResult.isExecuteFinished(): Boolean {
    return !status!!.ordinal.isProcessing() && errorMessage.isBlank()
}

fun TransactionResult.isFailed(): Boolean {
    if (isProcessing()) {
        return false
    }
    return errorMessage.isNotBlank()
}

private fun Int.isProcessing() = this < TransactionStatus.FINALIZED.ordinal && this >= TransactionStatus.UNKNOWN.ordinal

