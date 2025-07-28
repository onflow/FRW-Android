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
import com.flowfoundation.wallet.manager.config.NftCollection
import com.flowfoundation.wallet.manager.flow.FlowCadenceApi
import com.flowfoundation.wallet.manager.nft.NftCollectionStateManager
import com.flowfoundation.wallet.manager.staking.StakingManager
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.network.model.TokenInfo
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
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.webview.fcl.model.AuthzTransaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.Parcelize
import org.onflow.flow.infrastructure.parseErrorCode
import org.onflow.flow.websocket.FlowWebSocketClient
import org.onflow.flow.websocket.FlowWebSocketTopic
import org.onflow.flow.websocket.TransactionStatusPayload
import org.onflow.flow.models.TransactionExecution
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import java.lang.ref.WeakReference
import kotlin.math.abs

object TransactionStateManager {
    private val TAG = TransactionStateManager::class.java.simpleName

    private val cache by lazy { CacheManager<TransactionStateData>("transaction_state", TransactionStateData::class.java) }

    private lateinit var stateData: TransactionStateData

    private val onStateChangeCallbacks = mutableListOf<WeakReference<OnTransactionStateChange>>()

    private val txScriptMap = mutableMapOf<String, String>()

    // WebSocket client for real-time transaction monitoring
    private var webSocketClient: FlowWebSocketClient? = null
    private val activeSubscriptions = mutableMapOf<String, String>() // txId -> subscriptionId

    fun reload() {
        ioScope {
            stateData = cache.read() ?: TransactionStateData(
                mutableListOf()
            )
            // Start WebSocket monitoring for any existing unfinalized transactions
            initializeWebSocketMonitoring()
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

        // Immediately update the bubble stack so mini window appears right away
        updateState(transactionState)

        // Start WebSocket monitoring for this transaction
        subscribeToTransaction(transactionState.transactionId)
    }

    fun getLastVisibleTransaction(): TransactionState? { // update to use final?
        return stateData.data.toList().firstOrNull {
            (it.state < TransactionStatus.EXECUTED.ordinal && it.state > TransactionStatus.UNKNOWN.ordinal)
                    || (it.state == TransactionStatus.EXECUTED.ordinal && abs(it.updateTime - System.currentTimeMillis()) < 5000)
        }
    }

    fun getTransactionStateById(transactionId: String): TransactionState? {
        return stateData.data.toList().firstOrNull { it.transactionId == transactionId }
    }

    fun getProcessingTransaction(): List<TransactionState> {
        return stateData.data.toList().filter { it.isProcessing() }
    }

    private fun initializeWebSocketMonitoring() {
        ioScope {
            val processingTransactions = stateData.unsealedState()

            logd(TAG, "initializeWebSocketMonitoring: Found ${processingTransactions.size} processing transactions")

            if (processingTransactions.isNotEmpty()) {
                ensureWebSocketConnection()
                processingTransactions.forEach { state ->
                    subscribeToTransaction(state.transactionId)
                }
            }
        }
    }

    private suspend fun ensureWebSocketConnection() {
        if (webSocketClient == null) {
            logd(TAG, "Creating new WebSocket client for transaction monitoring")

            try {
                // Create HttpClient with WebSockets support (following flow-kmm test pattern)
                val httpClient = HttpClient {
                    install(WebSockets)
                }

                webSocketClient = FlowWebSocketClient(httpClient)
                webSocketClient?.connect(FlowWebSocketClient.MAINNET_WS_URL)
                logd(TAG, "WebSocket connected successfully")
            } catch (e: Exception) {
                logd(TAG, "Failed to connect WebSocket: ${e.message}")
                webSocketClient = null
                // Fall back to polling for existing transactions
                fallbackToPolling()
            }
        }
    }

    private fun subscribeToTransaction(transactionId: String) {
        ioScope {
            try {
                ensureWebSocketConnection()
                val wsClient = webSocketClient

                if (wsClient == null) {
                    logd(TAG, "WebSocket not available, falling back to polling for $transactionId")
                    fallbackToPollingForTransaction(transactionId)
                    return@ioScope
                }

                logd(TAG, "Subscribing to WebSocket updates for transaction: $transactionId")

                val subscription = wsClient.subscribeWithStrings(
                    topic = FlowWebSocketTopic.TRANSACTION_STATUSES.value,
                    arguments = mapOf("tx_id" to transactionId)
                )

                activeSubscriptions[transactionId] = subscription.subscriptionId
                logd(TAG, "WebSocket subscription created: ${subscription.subscriptionId} for tx: $transactionId")

                // Listen for status updates
                subscription.events
                    .onEach { response ->
                        handleWebSocketTransactionUpdate(transactionId, response.payload)
                    }
                    .catch { e ->
                        logd(TAG, "WebSocket subscription error for $transactionId: ${e.message}")
                        // Remove failed subscription and fall back to polling
                        activeSubscriptions.remove(transactionId)
                        fallbackToPollingForTransaction(transactionId)
                    }
                    .launchIn(kotlinx.coroutines.GlobalScope)

            } catch (e: Exception) {
                logd(TAG, "Failed to subscribe to transaction $transactionId via WebSocket: ${e.message}")
                fallbackToPollingForTransaction(transactionId)
            }
        }
    }

    private suspend fun handleWebSocketTransactionUpdate(transactionId: String, payload: kotlinx.serialization.json.JsonElement?) {
        try {
            if (payload == null) {
                logd(TAG, "Received null payload for transaction $transactionId")
                return
            }

            logd(TAG, "WebSocket update received for transaction: $transactionId")
            logd(TAG, "Payload: $payload")

            // Parse the transaction status payload
            val statusPayload = kotlinx.serialization.json.Json.decodeFromJsonElement(
                TransactionStatusPayload.serializer(),
                payload
            )

            val transactionResult = statusPayload.transactionResult
            val newStatus = transactionResult.status

            logd(TAG, "WebSocket: Transaction $transactionId status = $newStatus (ordinal=${newStatus?.ordinal})")

            // Find the transaction state
            val state = getTransactionStateById(transactionId)
            if (state == null) {
                logd(TAG, "WebSocket: Transaction $transactionId not found in state manager")
                return
            }

            // Check if anything changed (status OR execution)
            val currentExecution = state.execution
            val newExecution = transactionResult.execution?.name?.lowercase()
            val statusChanged = newStatus != null && newStatus.ordinal != state.state
            val executionChanged = newExecution != currentExecution
            
            if (statusChanged || executionChanged) {
                if (statusChanged) {
                    logd(TAG, "WebSocket: Updating transaction $transactionId from state ${state.state} to ${newStatus!!.ordinal}")
                    state.state = newStatus.ordinal
                }
                
                if (executionChanged) {
                    logd(TAG, "WebSocket: Updating transaction $transactionId execution from '${currentExecution}' to '${newExecution}'")
                    state.execution = newExecution
                }
                
                // Only set error message if there's an actual error or explicit execution failure
                state.errorMsg = when {
                    transactionResult.errorMessage.isNotBlank() -> transactionResult.errorMessage
                    transactionResult.execution == TransactionExecution.failure -> 
                        "Transaction execution failed"
                    else -> ""
                }
                
                updateState(state)

                // If transaction is completely finished (status + execution resolved), unsubscribe
                val isCompletelyFinished = state.isCompletelyFinished()
                logd(TAG, "WebSocket: Transaction $transactionId completeness check - isProcessing=${state.isProcessing()}, execution='${state.execution}', isCompletelyFinished=$isCompletelyFinished")
                
                if (isCompletelyFinished) {
                    logd(TAG, "WebSocket: Transaction $transactionId is completely finished, unsubscribing")
                    unsubscribeFromTransaction(transactionId)
                } else {
                    logd(TAG, "WebSocket: Transaction $transactionId still needs monitoring (execution='${state.execution}')")
                }
            } else {
                logd(TAG, "WebSocket: No changes for transaction $transactionId (status=${newStatus?.ordinal}, execution=${newExecution})")
            }

        } catch (e: Exception) {
            logd(TAG, "Error handling WebSocket update for $transactionId: ${e.message}")
            logd(TAG, "Exception details: ${e.javaClass.simpleName} - ${e.stackTraceToString()}")
        }
    }

    private suspend fun unsubscribeFromTransaction(transactionId: String) {
        val subscriptionId = activeSubscriptions.remove(transactionId)
        if (subscriptionId != null) {
            try {
                webSocketClient?.unsubscribe(subscriptionId)
                logd(TAG, "Unsubscribed from WebSocket updates for transaction: $transactionId")
            } catch (e: Exception) {
                logd(TAG, "Error unsubscribing from transaction $transactionId: ${e.message}")
            }
        }

        // Close WebSocket if no more active subscriptions
        if (activeSubscriptions.isEmpty()) {
            closeWebSocketConnection()
        }
    }

    private suspend fun closeWebSocketConnection() {
        try {
            webSocketClient?.close()
            webSocketClient = null
            logd(TAG, "WebSocket connection closed")
        } catch (e: Exception) {
            logd(TAG, "Error closing WebSocket connection: ${e.message}")
        }
    }

    // Fallback to polling when WebSocket is not available
    private fun fallbackToPolling() {
        ioScope {
            val stateQueue = stateData.unsealedState()
            stateQueue.forEach { state ->
                fallbackToPollingForTransaction(state.transactionId)
            }
        }
    }

    private fun fallbackToPollingForTransaction(transactionId: String) {
        ioScope {
            safeRunSuspend {
                try {
                    logd(TAG, "Fallback polling: Starting custom polling for $transactionId")
                    
                    // Custom polling that waits for BOTH status finalized AND execution resolved
                    var attempts = 0
                    val maxAttempts = 60 // 5 minutes max (60 * 5 seconds)
                    
                    while (attempts < maxAttempts) {
                        try {
                            val ret = FlowCadenceApi.waitForSeal(transactionId)
                            logd(TAG, "Fallback polling: Attempt ${attempts + 1} for $transactionId - status=${ret.status}, execution=${ret.execution}")

                            val state = getTransactionStateById(transactionId)
                            if (state != null) {
                                val statusChanged = ret.status!!.ordinal != state.state
                                val executionChanged = ret.execution?.name?.lowercase() != state.execution
                                
                                if (statusChanged || executionChanged) {
                                    if (statusChanged) {
                                        logd(TAG, "Fallback polling: State changed for $transactionId from ${state.state} to ${ret.status!!.ordinal}")
                                        state.state = ret.status!!.ordinal
                                    }
                                    
                                    if (executionChanged) {
                                        logd(TAG, "Fallback polling: Execution changed for $transactionId from '${state.execution}' to '${ret.execution?.name?.lowercase()}'")
                                        state.execution = ret.execution?.name?.lowercase()
                                    }
                                    
                                    // Only set error message if there's an actual error or explicit execution failure
                                    state.errorMsg = when {
                                        ret.errorMessage.isNotBlank() -> ret.errorMessage
                                        ret.execution == TransactionExecution.failure -> 
                                            "Transaction execution failed"
                                        else -> ""
                                    }
                                    
                                    updateState(state)
                                }
                                
                                // Check if completely finished
                                if (state.isCompletelyFinished()) {
                                    logd(TAG, "Fallback polling: Transaction $transactionId completely finished")
                                    break
                                }
                            }
                            
                            // Wait 5 seconds before next check if not finished
                            delay(5000)
                            attempts++
                            
                        } catch (e: Exception) {
                            logd(TAG, "Fallback polling attempt ${attempts + 1} failed for $transactionId: ${e.message}")
                            attempts++
                            delay(5000)
                        }
                    }
                    
                    if (attempts >= maxAttempts) {
                        logd(TAG, "Fallback polling timeout for $transactionId after $maxAttempts attempts")
                        val state = getTransactionStateById(transactionId)
                        if (state != null) {
                            state.state = TransactionStatus.EXPIRED.ordinal
                            state.errorMsg = "Transaction monitoring timeout"
                            updateState(state)
                        }
                    }
                    
                } catch (e: Exception) {
                    logd(TAG, "Fallback polling failed for $transactionId: ${e.message}")
                    val state = getTransactionStateById(transactionId)
                    if (state != null) {
                        state.state = TransactionStatus.EXPIRED.ordinal
                        state.errorMsg = e.message
                        updateState(state)
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
        if (state.isCompletelyFinished()) {
            uiScope {
                delay(3000)
                popBubbleStack(state)
                
                // Show completion toast based on transaction type and result
                when (state.type) {
                    TransactionState.TYPE_MOVE_NFT -> {
                        if (state.isSuccess()) {
                            toast(R.string.move_nft_success)
                        } else {
                            toast(R.string.move_nft_failed)
                        }
                    }
                    TransactionState.TYPE_TRANSFER_NFT -> {
                        if (state.isSuccess()) {
                            toast(R.string.send_nft_success)
                        } else {
                            toast(R.string.send_nft_failed)
                        }
                    }
                    TransactionState.TYPE_TRANSFER_COIN -> {
                        if (state.isSuccess()) {
                            toast(R.string.transfer_success)
                        } else {
                            toast(R.string.transfer_failed)
                        }
                    }
                    // Add other transaction types as needed
                }
                
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
                FungibleTokenListManager.updateTokenList()
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
            logd(TAG, "unsealedState: TX ${tx.transactionId.take(8)}... state=${tx.state} execution=${tx.execution} isCompletelyFinished=${tx.isCompletelyFinished()}")
        }
        // Return transactions that are not completely finished (need monitoring)
        val result = allTransactions.filter { !it.isCompletelyFinished() }
        logd(TAG, "unsealedState: Returning ${result.size} transactions needing monitoring")
        return result
    }

    private fun Int.isProcessing() = this < TransactionStatus.EXECUTED.ordinal && this >= TransactionStatus.UNKNOWN.ordinal
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
    
    @SerializedName("execution")
    var execution: String? = "pending", // "success", "failure", "pending", or null
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

    fun tokenData() = Gson().fromJson(data, TokenInfo::class.java)

    fun nftCollectionData() = Gson().fromJson(data, NftCollection::class.java)

    fun nftSendData() = Gson().fromJson(data, NftSendModel::class.java)

    fun fclTransactionData() = Gson().fromJson(data, AuthzTransaction::class.java)

    fun contact() = if (type == TYPE_TRANSFER_COIN) coinData().target else nftData().target

    fun isSuccess(): Boolean {
        // Only consider successful if transaction is finalized AND execution is explicitly "success"
        return state >= TransactionStatus.EXECUTED.ordinal && 
               errorMsg.isNullOrBlank() && 
               execution == "success"
    }

    fun isFailed(): Boolean {
        if (isProcessing()) {
            return false
        }
        if (isExpired()) {
            return true
        }
        // Transaction is failed if there's an error message OR execution is explicitly "failure"
        return !errorMsg.isNullOrBlank() || execution == "failure"
    }

    fun isProcessing() = state < TransactionStatus.EXECUTED.ordinal

    /**
     * Check if transaction is truly complete (both status finalized AND execution resolved)
     * Used to determine when to stop monitoring
     */
    fun isCompletelyFinished(): Boolean {
        val notProcessing = !isProcessing()
        val executionResolved = execution == "success" || execution == "failure"
        val expired = isExpired()
        val result = notProcessing && (executionResolved || expired)
        
        return result
    }

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
            TransactionStatus.EXECUTED.ordinal -> 1.0f
            TransactionStatus.SEALED.ordinal-> 1.0f
            else -> 0.0f
        }
    }
}