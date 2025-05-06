package com.flowfoundation.wallet.manager.transaction

import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.manager.account.model.StorageLimitDialogType
import com.nftco.flow.sdk.FlowId
import com.nftco.flow.sdk.FlowTransactionResult
import org.onflow.flow.models.TransactionStatus
import org.onflow.flow.models.hexToBytes
import com.flowfoundation.wallet.manager.flowjvm.FlowApi
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.page.storage.StorageLimitErrorDialog
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.safeRun
import com.flowfoundation.wallet.utils.uiScope
import com.nftco.flow.sdk.FlowError
import com.nftco.flow.sdk.parseErrorCode
import kotlinx.coroutines.delay

private val TAG = TransactionStateWatcher::class.java.simpleName

class TransactionStateWatcher(
    val transactionId: String,
) {

    suspend fun watch(callback: (state: FlowTransactionResult) -> Unit) {
        var ret: FlowTransactionResult? = null
        var statusCode = -1
        while (true) {
            safeRun {
                val result = checkNotNull(
                    FlowApi.get().getTransactionResultById(FlowId.of(transactionId.hexToBytes()))
                ) { "Transaction with that id not found" }
                logd(TAG, "statusCode:${result.status.num}")
                if (result.status.num != statusCode) {
                    statusCode = result.status.num
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
