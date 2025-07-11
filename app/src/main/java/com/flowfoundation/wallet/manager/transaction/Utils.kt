package com.flowfoundation.wallet.manager.transaction

import org.onflow.flow.models.TransactionExecution
import org.onflow.flow.models.TransactionResult
import org.onflow.flow.models.TransactionStatus

const val ERROR_STORAGE_CAPACITY_EXCEEDED = 1103

fun TransactionResult.isProcessing(): Boolean {
    return status!!.ordinal.isProcessing()
}

fun TransactionResult.isExecuteFinished(): Boolean {
    return when (status) {
        TransactionStatus.FINALIZED -> execution == TransactionExecution.success && errorMessage.isBlank()
        TransactionStatus.SEALED -> execution == TransactionExecution.success && errorMessage.isBlank()
        TransactionStatus.EXECUTED -> execution == TransactionExecution.success && errorMessage.isBlank()
        else -> false
    }
}

fun TransactionResult.isFailed(): Boolean {
    if (isProcessing()) {
        return false
    }
    // Transaction is failed if:
    // 1. It has an error message, OR  
    // 2. It's finalized/sealed/executed but execution is explicitly "failure", OR
    // 3. Transaction is expired
    return errorMessage.isNotBlank() || 
           (status in listOf(TransactionStatus.FINALIZED, TransactionStatus.SEALED, TransactionStatus.EXECUTED) && 
            execution == TransactionExecution.failure) ||
           status == TransactionStatus.EXPIRED
}

private fun Int.isProcessing() = this < TransactionStatus.FINALIZED.ordinal && this >= TransactionStatus.UNKNOWN.ordinal

