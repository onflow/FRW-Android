package com.flowfoundation.wallet.manager.transaction

import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.manager.account.model.StorageLimitDialogType
import com.flowfoundation.wallet.manager.flow.FlowCadenceApi
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.page.storage.StorageLimitErrorDialog
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.safeRunSuspend
import com.flowfoundation.wallet.utils.uiScope
import org.onflow.flow.infrastructure.parseErrorCode
import org.onflow.flow.models.TransactionExecution
import org.onflow.flow.models.TransactionResult
import org.onflow.flow.models.TransactionStatus

private val TAG = TransactionStateWatcher::class.java.simpleName

class TransactionStateWatcher(
    val transactionId: String,
) {

    suspend fun watch(callback: (state: TransactionResult) -> Unit) {
        safeRunSuspend {
            try {
                val result = FlowCadenceApi.waitForSeal(transactionId)
                callback.invoke(result)
                
                // Report the final result
                MixpanelManager.transactionResult(
                    transactionId,
                    result.isSuccess(),
                    result.errorMessage
                )
                
                // Handle storage errors
                if (result.errorMessage.isNotBlank()) {
                    val errorCode = parseErrorCode(result.errorMessage)
                    ErrorReporter.reportTransactionError(transactionId, errorCode ?: -1)
                    uiScope {
                        when (errorCode) {
                            ERROR_STORAGE_CAPACITY_EXCEEDED -> {
                                BaseActivity.getCurrentActivity()?.let {
                                    StorageLimitErrorDialog(it, StorageLimitDialogType.LIMIT_REACHED_ERROR).show()
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logd(TAG, "Transaction $transactionId failed or timed out: ${e.message}")
                
                // Create a failed result and notify callback
                val failedResult = TransactionResult(
                    blockId = "",
                    status = TransactionStatus.EXPIRED,
                    statusCode = TransactionStatus.EXPIRED.ordinal,
                    errorMessage = e.message ?: "Transaction timeout",
                    computationUsed = "0",
                    events = emptyList(),
                    execution = null,
                    links = null
                )
                callback.invoke(failedResult)
                
                // Report the failure
                MixpanelManager.transactionResult(transactionId, false, e.message)
                ErrorReporter.reportTransactionError(transactionId, -1)
            }
        }
    }

    private fun TransactionResult.isSuccess(): Boolean {
        return when (status) {
            TransactionStatus.FINALIZED -> execution == TransactionExecution.success && errorMessage.isBlank()
            TransactionStatus.SEALED -> execution == TransactionExecution.success && errorMessage.isBlank()
            TransactionStatus.EXECUTED -> execution == TransactionExecution.success && errorMessage.isBlank()
            else -> false
        }
    }

}
