package com.flowfoundation.wallet.manager.flow

import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.manager.flowjvm.FlowAddressRegistry
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import org.onflow.flow.ChainId
import org.onflow.flow.FlowApi
import org.onflow.flow.infrastructure.Cadence
import org.onflow.flow.models.Account
import org.onflow.flow.AddressRegistry
import org.onflow.flow.models.BlockHeader
import org.onflow.flow.models.Transaction
import org.onflow.flow.models.TransactionResult
import org.onflow.flow.models.BlockStatus

object FlowCadenceApi {

    private const val TAG = "FlowCadenceApi"
    private var api: FlowApi? = null
    var DEFAULT_CHAIN_ID: ChainId = ChainId.Mainnet
    var DEFAULT_ADDRESS_REGISTRY: AddressRegistry = AddressRegistry()

    fun refreshConfig() {
        logd(TAG, "refreshConfig start")
        logd(TAG, "chainId:${chainId()}")
        DEFAULT_CHAIN_ID = chainId()
        DEFAULT_ADDRESS_REGISTRY = FlowAddressRegistry().addressRegistry()
        api = FlowApi(chainId())
    }

    private fun get(): FlowApi {
        if (DEFAULT_CHAIN_ID != chainId()) {
            refreshConfig()
        }
        return api ?: FlowApi(chainId())
    }

    private fun chainId() = when {
        isTestnet() -> ChainId.Testnet
        else -> ChainId.Mainnet
    }

    private fun flowScript(block: CadenceScriptBuilder.() -> Unit): CadenceScriptBuilder {
        val ret = CadenceScriptBuilder()
        block(ret)
        return ret
    }

    suspend fun executeCadenceScript(block: CadenceScriptBuilder.() -> Unit): Cadence.Value {
        val api = get()
        val builder = flowScript(block)
        return try {
            api.executeScript(
                script = builder.script,
                arguments = builder.arguments
            )
        } catch (t: Throwable) {
            throw Error("Error while running script", t)
        }
    }

    suspend fun getAccount(address: String, blockHeight: String? = null, sealed: BlockStatus = BlockStatus.FINAL): Account {
        try {
            val account = get().getAccount(address, blockHeight, sealed)
            if (account.address.isBlank() || account.balance.isBlank()) {
                loge(TAG, "Invalid account response - missing required fields")
                throw IllegalStateException("Invalid account response - missing required fields")
            }
            return account
        } catch (e: Exception) {
            loge(TAG, "Error getting account: ${e.message}")
            throw e
        }
    }

    suspend fun getBlockHeader(id: String?, blockHeight: String? = null, sealed: BlockStatus = BlockStatus.FINAL): BlockHeader {
        return get().getBlockHeader(id, blockHeight, sealed)
    }

    suspend fun getTransactionResultById(transactionId: String): TransactionResult {
        return get().getTransactionResult(transactionId)
    }

    suspend fun getTransaction(transactionId: String): Transaction {
        return get().getTransaction(transactionId)
    }

    suspend fun sendTransaction(transaction: Transaction): Transaction {
        return get().sendTransaction(transaction)
    }

    suspend fun waitForSeal(transactionId: String): TransactionResult {
        return get().waitForSeal(transactionId)
    }

}