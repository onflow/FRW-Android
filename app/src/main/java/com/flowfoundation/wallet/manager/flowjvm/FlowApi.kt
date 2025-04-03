package com.flowfoundation.wallet.manager.flowjvm

import com.fasterxml.jackson.databind.DeserializationFeature
import com.nftco.flow.sdk.Flow
import com.nftco.flow.sdk.FlowAccessApi
import com.nftco.flow.sdk.FlowChainId
import com.nftco.flow.sdk.impl.FlowAccessApiImpl
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.utils.logd

internal object FlowApi {
    private const val HOST_MAINNET = "access.mainnet.nodes.onflow.org"
    private const val HOST_TESTNET = "access.devnet.nodes.onflow.org"

    private var api: FlowAccessApi? = null

    init {
        Flow.OBJECT_MAPPER.configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true)
    }

    fun refreshConfig() {
        logd("FlowApi", "refreshConfig start")
        logd("FlowApi", "chainId:${chainId()}")
        (api as? FlowAccessApiImpl)?.close()
        Flow.configureDefaults( // to-do
            chainId = chainId(),
            addressRegistry = FlowAddressRegistry().addressRegistry()
        )
        api = Flow.newAccessApi(host(), 9000)
        logd("FlowApi", "DEFAULT_CHAIN_ID:${Flow.DEFAULT_CHAIN_ID}")
        logd("FlowApi", "DEFAULT_ADDRESS_REGISTRY:${Flow.DEFAULT_ADDRESS_REGISTRY}")
        logd("FlowApi", "isTestnet():${isTestnet()}")
        logd("FlowApi", "refreshConfig end")
    }

    fun get(): FlowAccessApi {
        val chainId = chainId()
        if (Flow.DEFAULT_CHAIN_ID != chainId) {
            refreshConfig()
        }
        return api ?: Flow.newAccessApi(host(), 9000)
    }

    private fun chainId() = when {
        isTestnet() -> FlowChainId.TESTNET
        else -> FlowChainId.MAINNET
    }

    private fun host() = when {
        isTestnet() -> HOST_TESTNET
        else -> HOST_MAINNET
    }
}