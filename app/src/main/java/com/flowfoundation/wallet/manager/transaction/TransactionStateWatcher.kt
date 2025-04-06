package com.flowfoundation.wallet.manager.transaction

import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.manager.account.model.StorageLimitDialogType
import com.flowfoundation.wallet.manager.flow.FlowCadenceApi
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.page.storage.StorageLimitErrorDialog
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.safeRunSuspend
import com.flowfoundation.wallet.utils.uiScope
import org.onflow.flow.infrastructure.parseErrorCode
import kotlinx.coroutines.delay
import org.onflow.flow.models.TransactionResult
import org.onflow.flow.models.TransactionStatus

private val TAG = TransactionStateWatcher::class.java.simpleName

class TransactionStateWatcher(
    val transactionId: String,
) {

    suspend fun watch(callback: (state: TransactionResult) -> Unit) {
        var ret: TransactionResult? = null
        var statusCode = -1
        while (true) {
            safeRunSuspend {
                val result = checkNotNull(
                    FlowCadenceApi.getTransactionResultById(transactionId)
                ) { "Transaction with that id not found" }
                logd(TAG, "statusCode:${result.status.ordinal}")
                if (result.status.ordinal != statusCode) {
                    statusCode = result.status.ordinal
                    callback.invoke(result)
                }
                ret = result
            }

            if (!statusCode.isProcessing() || !ret?.errorMessage.isNullOrBlank()) {
                MixpanelManager.transactionResult(
                    transactionId,
                    statusCode.isProcessing().not() && ret?.errorMessage.isNullOrBlank(),
                    ret?.errorMessage
                )
                uiScope {
                    if (!ret?.errorMessage.isNullOrBlank()) {
                        when (parseErrorCode(ret?.errorMessage.orEmpty())) {
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
