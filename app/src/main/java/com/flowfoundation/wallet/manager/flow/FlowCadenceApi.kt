package com.flowfoundation.wallet.manager.flow

import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.manager.flowjvm.FlowAddressRegistry
import com.flowfoundation.wallet.utils.logd
import com.nftco.flow.sdk.AddressRegistry
import com.nftco.flow.sdk.Flow
import com.nftco.flow.sdk.FlowException
import org.onflow.flow.ChainId
import org.onflow.flow.FlowApi
import org.onflow.flow.infrastructure.Cadence

object FlowCadenceApi {

    private const val TAG = "FlowCadenceApi"
    private var api: FlowApi? = null
    var DEFAULT_CHAIN_ID: ChainId = ChainId.Mainnet
    var DEFAULT_ADDRESS_REGISTRY: AddressRegistry = Flow.DEFAULT_ADDRESS_REGISTRY

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
            throw FlowException("Error while running script", t)
        }
    }

}