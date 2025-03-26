package com.flowfoundation.wallet.manager.flowjvm

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.onflow.flow.sdk.Flow
import org.onflow.flow.sdk.FlowAccessApi
import org.onflow.flow.sdk.FlowChainId
import org.onflow.flow.sdk.impl.FlowAccessApiImpl
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.utils.logd

internal object FlowApi {
    private const val HOST_MAINNET = "access.mainnet.nodes.onflow.org"
    private const val HOST_TESTNET = "access.devnet.nodes.onflow.org"

    private var api: FlowAccessApi? = null
    
    private val jsonMapper = ObjectMapper().apply {
        registerKotlinModule()
        configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true)
    }

    fun refreshConfig() {
        logd("FlowApi", "refreshConfig start")
        logd("FlowApi", "chainId:${chainId()}")
        (api as? FlowAccessApiImpl)?.close()
        Flow.configureDefaults(
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

    fun toJson(value: Any): String = jsonMapper.writeValueAsString(value)
    
    fun fromJson(json: String, type: Class<*>): Any = jsonMapper.readValue(json, type)
}

fun <T> FlowAccessApi.AccessApiCallResponse<T>.getOrNull(): T? {
    return when (this) {
        is FlowAccessApi.AccessApiCallResponse.Success -> data
        is FlowAccessApi.AccessApiCallResponse.Error -> null
    }
}

fun <T> FlowAccessApi.AccessApiCallResponse<T>.getOrThrow(): T {
    return when (this) {
        is FlowAccessApi.AccessApiCallResponse.Success -> data
        is FlowAccessApi.AccessApiCallResponse.Error -> throw throwable ?: RuntimeException(message)
    }
}