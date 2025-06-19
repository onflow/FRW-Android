package com.flowfoundation.wallet.manager.transaction

import android.os.Parcelable
import androidx.annotation.MainThread
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import org.onflow.flow.models.TransactionStatus
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.cache.CacheManager
import com.flowfoundation.wallet.manager.account.model.StorageLimitDialogType
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.TokenStateManager
import com.flowfoundation.wallet.manager.config.NftCollection
import com.flowfoundation.wallet.manager.flow.FlowCadenceApi
import com.flowfoundation.wallet.manager.nft.NftCollectionStateManager
import com.flowfoundation.wallet.manager.staking.StakingManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.page.send.nft.NftSendModel
import com.flowfoundation.wallet.page.send.transaction.subpage.amount.model.TransactionModel
import com.flowfoundation.wallet.page.storage.StorageLimitErrorDialog
import com.flowfoundation.wallet.page.window.bubble.tools.popBubbleStack
import com.flowfoundation.wallet.page.window.bubble.tools.updateBubbleStack
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.safeRunSuspend
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.webview.fcl.model.AuthzTransaction
import kotlinx.coroutines.delay
import kotlinx.parcelize.Parcelize
import org.onflow.flow.infrastructure.parseErrorCode
import java.lang.ref.WeakReference
import kotlin.math.abs

object TransactionStateManager {
    private val TAG = TransactionStateManager::class.java.simpleName

    private val cache by lazy { CacheManager<TransactionStateData>("transaction_state", TransactionStateData::class.java) }

    private lateinit var stateData: TransactionStateData

    private val onStateChangeCallbacks = mutableListOf<WeakReference<OnTransactionStateChange>>()

    private val txScriptMap = mutableMapOf<String, String>()

    fun reload() {
        ioScope {
            stateData = cache.read() ?: TransactionStateData(
                mutableListOf()
            )
            loopState()
        }
    }

    fun recordTransactionScript(txId: String, script: String) {
        txScriptMap[txId] = script
    }

    fun getScriptId(txId: String): String {
        return txScriptMap[txId] ?: ""
    }

    fun getTransactionStateList() = stateData.data.toList()

    fun addOnTransactionStateChange(callback: OnTransactionStateChange) {
        onStateChangeCallbacks.add(WeakReference(callback))
    }

    fun removeOnTransactionStateCallback(callback: OnTransactionStateChange) {
        val index = onStateChangeCallbacks.indexOfLast { weakReference -> weakReference.get() == callback }
        onStateChangeCallbacks.removeAt(index)
    }

    @MainThread
    fun newTransaction(transactionState: TransactionState) {
        if (stateData.data.toList().firstOrNull { it.transactionId == transactionState.transactionId } != null) {
            return
        }
        stateData.data.add(transactionState)
        updateState(transactionState)
        loopState()
    }

    fun getLastVisibleTransaction(): TransactionState? {
        return stateData.data.toList().firstOrNull {
            (it.state < TransactionStatus.FINALIZED.ordinal && it.state > TransactionStatus.UNKNOWN.ordinal)
                    || (it.state == TransactionStatus.FINALIZED.ordinal && abs(it.updateTime - System.currentTimeMillis()) < 5000)
        }
    }

    fun getTransactionStateById(transactionId: String): TransactionState? {
        return stateData.data.toList().firstOrNull { it.transactionId == transactionId }
    }

    fun getProcessingTransaction(): List<TransactionState> {
        return stateData.data.toList().filter { it.isProcessing() }
    }

    private fun loopState() {
        ioScope {
            val stateQueue = stateData.unsealedState()

            logd(TAG, "loopState: Found ${stateQueue.size} unsealed transactions to monitor")
            if (stateQueue.isEmpty()) {
                return@ioScope
            }

            // Process each transaction individually with waitForSeal for efficiency
            stateQueue.forEach { state ->
                logd(TAG, "loopState: Starting monitoring for transaction ${state.transactionId} (current state=${state.state})")
                ioScope {
                    safeRunSuspend {
                        try {
                            logd(TAG, "loopState: Calling waitForSeal for ${state.transactionId}")
                            val ret = FlowCadenceApi.waitForSeal(state.transactionId)
                            logd(TAG, "loopState: waitForSeal returned for ${state.transactionId} - status=${ret.status}, ordinal=${ret.status?.ordinal}")
                            
                            if (ret.status!!.ordinal != state.state) {
                                logd(TAG, "loopState: State changed for ${state.transactionId} from ${state.state} to ${ret.status!!.ordinal}")
                                state.state = ret.status!!.ordinal
                                state.errorMsg = ret.errorMessage
                                updateState(state)
                            } else {
                                logd(TAG, "loopState: No state change for ${state.transactionId}")
                            }
                        } catch (e: Exception) {
                            logd(TAG, "loopState: Error monitoring transaction ${state.transactionId}: ${e.message}")
                            state.state = TransactionStatus.EXPIRED.ordinal
                            state.errorMsg = e.message
                            updateState(state)
                        }
                    }
                }
            }
        }
    }

    private fun updateState(state: TransactionState) {
        state.updateTime = System.currentTimeMillis()
        ioScope { cache.cache(stateData) }
        logd(TAG, "updateState:$state")
        dispatchCallback()
        updateBubbleStack(state)
        if (!state.isProcessing()) {
            uiScope {
                delay(3000)
                popBubbleStack(state)
                if (state.isFailed()) {
                    val errorCode = parseErrorCode(state.errorMsg.orEmpty())
                    ErrorReporter.reportTransactionError(state.transactionId, errorCode ?: -1)
                    when (errorCode) {
                        ERROR_STORAGE_CAPACITY_EXCEEDED -> {
                            BaseActivity.getCurrentActivity()?.let {
                                StorageLimitErrorDialog(it, StorageLimitDialogType.LIMIT_REACHED_ERROR).show()
                            }
                        }
                    }
                }
            }
            MixpanelManager.transactionResult(state.transactionId, state.isSuccess(), state.errorMsg)
        }
        ioScope {
            if (state.type == TransactionState.TYPE_ADD_TOKEN && state.isSuccess()) {
                TokenStateManager.fetchStateSingle(state.tokenData(), cache = true)
            }

            if (state.type == TransactionState.TYPE_ENABLE_NFT && state.isSuccess()) {
                NftCollectionStateManager.fetchState()
            }

            if (state.type == TransactionState.TYPE_STAKE_FLOW && state.isSuccess()) {
                StakingManager.refresh()
            }
        }
    }

    private fun dispatchCallback() {
        uiScope {
            onStateChangeCallbacks.removeAll { it.get() == null }
            onStateChangeCallbacks.forEach { it.get()?.onTransactionStateChange() }
        }
    }

    private fun TransactionStateData.unsealedState(): List<TransactionState> {
        val allTransactions = data.toList()
        logd(TAG, "unsealedState: Total transactions in cache: ${allTransactions.size}")
        allTransactions.forEach { tx ->
            logd(TAG, "unsealedState: TX ${tx.transactionId.take(8)}... state=${tx.state} isProcessing=${tx.state.isProcessing()}")
        }
        val result = allTransactions.filter { it.state.isProcessing() }
        logd(TAG, "unsealedState: Returning ${result.size} processing transactions")
        return result
    }

    private fun Int.isProcessing() = this < TransactionStatus.FINALIZED.ordinal && this >= TransactionStatus.UNKNOWN.ordinal
}

interface OnTransactionStateChange {
    fun onTransactionStateChange()
}


class TransactionStateData(
    @SerializedName("data")
    val data: MutableList<TransactionState>,
)

@Parcelize
data class TransactionState(
    @SerializedName("transactionId")
    val transactionId: String,
    @SerializedName("time")
    val time: Long,
    @SerializedName("updateTime")
    var updateTime: Long = 0,

    // @FlowTransactionStatus
    @SerializedName("state")
    var state: Int,
    @SerializedName("type")
    val type: Int,

    /**
     * TYPE_COIN = TransactionModel
     * TYPE_NFT = NftSendModel
     */
    @SerializedName("data")
    val data: String,

    @SerializedName("errorMsg")
    var errorMsg: String? = null,
) : Parcelable {
    companion object {
        const val TYPE_TRANSACTION_DEFAULT = 0
        const val TYPE_NFT = 1
        const val TYPE_TRANSFER_COIN = 2
        const val TYPE_ADD_TOKEN = 3

        // enable nft collection
        const val TYPE_ENABLE_NFT = 4
        const val TYPE_TRANSFER_NFT = 5

        // transaction from browser
        const val TYPE_FCL_TRANSACTION = 6

        const val TYPE_STAKE_FLOW = 8

        const val TYPE_REVOKE_KEY = 9

        const val TYPE_ADD_PUBLIC_KEY = 10

        const val TYPE_MOVE_NFT = 11
    }

    fun coinData() = Gson().fromJson(data, TransactionModel::class.java)

    fun nftData() = Gson().fromJson(data, NftSendModel::class.java)

    fun tokenData() = Gson().fromJson(data, FlowCoin::class.java)

    fun nftCollectionData() = Gson().fromJson(data, NftCollection::class.java)

    fun nftSendData() = Gson().fromJson(data, NftSendModel::class.java)

    fun fclTransactionData() = Gson().fromJson(data, AuthzTransaction::class.java)

    fun contact() = if (type == TYPE_TRANSFER_COIN) coinData().target else nftData().target

    fun isSuccess() = state >= TransactionStatus.FINALIZED.ordinal && errorMsg.isNullOrBlank()

    fun isFailed(): Boolean {
        if (isProcessing()) {
            return false
        }
        if (isExpired()) {
            return true
        }
        return !errorMsg.isNullOrBlank()
    }

    fun isProcessing() = state < TransactionStatus.FINALIZED.ordinal

    private fun isExpired() = state == TransactionStatus.EXPIRED.ordinal

    fun stateStr() = if (isSuccess()) {
        R.string.success.res2String()
    } else if (isFailed()) {
        R.string.failed.res2String()
    } else {
        R.string.pending.res2String()
    }

    fun progress(): Float {
        return when (state) {
            TransactionStatus.UNKNOWN.ordinal, TransactionStatus.PENDING.ordinal -> 0.25f
            TransactionStatus.FINALIZED.ordinal -> 1.0f
            TransactionStatus.EXECUTED.ordinal -> 1.0f
            TransactionStatus.SEALED.ordinal-> 1.0f
            else -> 0.0f
        }
    }
}