package com.flowfoundation.wallet.manager.app

import com.flowfoundation.wallet.manager.config.NftCollectionConfig
import com.flowfoundation.wallet.utils.NETWORK_MAINNET
import com.flowfoundation.wallet.utils.NETWORK_SANDBOX
import com.flowfoundation.wallet.utils.NETWORK_TESTNET
import com.flowfoundation.wallet.utils.cpuScope
import com.flowfoundation.wallet.utils.getChainNetworkPreference
import com.flowfoundation.wallet.utils.isDev
import com.flowfoundation.wallet.utils.isDeveloperModeEnable
import com.flowfoundation.wallet.utils.isTesting
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope

private var network = if (isDev() || isTesting()) NETWORK_TESTNET else NETWORK_MAINNET
private var isDeveloperMode = isDev() || isTesting()

fun refreshChainNetwork(callback: (() -> Unit)? = null) {
    cpuScope {
        refreshChainNetworkSync()
        uiScope { callback?.invoke() }
    }
}

suspend fun refreshChainNetworkSync() {
    logd("refreshChainNetwork", "start")
    isDeveloperMode = isDeveloperModeEnable()
    network = getChainNetworkPreference()
    logd("refreshChainNetwork", "end")
}

fun chainNetwork() = network
fun isDeveloperMode() = isDeveloperMode

fun isMainnet() = network == NETWORK_MAINNET
fun isTestnet() = network == NETWORK_TESTNET
fun isSandboxNet() = network == NETWORK_SANDBOX


fun chainNetWorkString(): String {
    return when {
        isTestnet() -> NETWORK_NAME_TESTNET
        isSandboxNet() -> NETWORK_NAME_SANDBOX
        else -> NETWORK_NAME_MAINNET
    }
}

fun chainNetWorkString(network: Int): String {
    return when (network) {
        NETWORK_TESTNET -> NETWORK_NAME_TESTNET
        NETWORK_SANDBOX -> NETWORK_NAME_SANDBOX
        else -> NETWORK_NAME_MAINNET
    }
}

fun networkId(network: String): Int {
    return when (network) {
        NETWORK_NAME_TESTNET -> NETWORK_TESTNET
        NETWORK_NAME_SANDBOX -> NETWORK_SANDBOX
        else -> NETWORK_MAINNET
    }
}

fun doNetworkChangeTask() {
    NftCollectionConfig.sync()
}

const val NETWORK_NAME_MAINNET = "mainnet"
const val NETWORK_NAME_TESTNET = "testnet"
const val NETWORK_NAME_SANDBOX = "sandboxnet"