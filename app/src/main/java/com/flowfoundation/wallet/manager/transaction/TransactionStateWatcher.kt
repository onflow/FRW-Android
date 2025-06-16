package com.flowfoundation.wallet.manager.transaction

import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.manager.account.model.StorageLimitDialogType
import com.flowfoundation.wallet.manager.flow.FlowCadenceApi
import org.onflow.flow.models.TransactionStatus
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.page.storage.StorageLimitErrorDialog
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.safeRunSuspend
import com.flowfoundation.wallet.utils.uiScope
import kotlinx.coroutines.delay
import org.onflow.flow.infrastructure.parseErrorCode
import org.onflow.flow.models.TransactionResult

private val TAG = TransactionStateWatcher::class.java.simpleName

class TransactionStateWatcher(
    val transactionId: String,
) {

    suspend fun watch(callback: (state: TransactionResult) -> Unit) {
        var ret: TransactionResult? = null
        var statusCode = -1
        while (true) {
            safeRunSuspend {
                val result = try {
                    checkNotNull(
                        FlowCadenceApi.getTransactionResultById(transactionId)
                    ) { "Transaction with that id not found" }
                } catch (e: kotlinx.serialization.SerializationException) {
                    logd(TAG, "Transaction result parsing failed for $transactionId due to JSON deserialization error: ${e.message}")
                    throw e

                } catch (e: RuntimeException) {
                    if (e.message?.contains("Illegal input: Expected JsonPrimitive") == true || 
                        e.message?.contains("serialization") == true ||
                        e.message?.contains("deserialization") == true) {
                        logd(TAG, "Transaction result parsing failed for $transactionId due to Flow SDK JSON parsing error: ${e.message}")
                    }
                    throw e
                }
                
                if (result.status!!.ordinal != statusCode) {
                    statusCode = result.status!!.ordinal
                    callback.invoke(result)
                }
                ret = result
            }
            val errorMsg = ret?.errorMessage

            if (!statusCode.isProcessing() || !errorMsg.isNullOrBlank()) {
                MixpanelManager.transactionResult(
                    transactionId,
                    statusCode.isProcessing().not() && errorMsg.isNullOrBlank(),
                    ret?.errorMessage
                )
                uiScope {
                    if (!errorMsg.isNullOrBlank()) {
                        val errorCode = parseErrorCode(errorMsg)
                        ErrorReporter.reportTransactionError(transactionId, errorCode ?: -1)
                        when (errorCode) {
                            ERROR_STORAGE_CAPACITY_EXCEEDED -> {
                                BaseActivity.getCurrentActivity()?.let {
                                    StorageLimitErrorDialog(it, StorageLimitDialogType.LIMIT_REACHED_ERROR).show()
                                }
                            }
                        }
                    }
                }
                break
            }

            delay(500)
        }
    }

    private fun Int.isProcessing() = this < TransactionStatus.SEALED.ordinal && this >= TransactionStatus.UNKNOWN.ordinal

}
