package com.flowfoundation.wallet.manager.transaction

import com.nftco.flow.sdk.FlowTransactionResult
import com.nftco.flow.sdk.FlowTransactionStatus

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

private fun Int.isProcessing() = this < FlowTransactionStatus.SEALED.num && this >= FlowTransactionStatus.UNKNOWN.num

private fun Int.isUnknown() = this == FlowTransactionStatus.UNKNOWN.num || this == FlowTransactionStatus.EXPIRED.num