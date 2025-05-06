package com.flowfoundation.wallet.manager.transaction

import com.nftco.flow.sdk.FlowTransactionResult
import org.onflow.flow.models.TransactionStatus

const val ERROR_STORAGE_CAPACITY_EXCEEDED = 1103

fun FlowTransactionResult.isProcessing(): Boolean {
    return status.num.isProcessing()
}

fun FlowTransactionResult.isExecuteFinished(): Boolean {
    return !status.num.isProcessing() && errorMessage.isBlank()
}

fun FlowTransactionResult.isFailed(): Boolean {
    if (isProcessing()) {
        return false
    }
    return errorMessage.isNotBlank()
}

private fun Int.isProcessing() = this < TransactionStatus.SEALED.ordinal && this >= TransactionStatus.UNKNOWN.ordinal

