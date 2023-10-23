package io.outblock.lilico.manager.app

import io.outblock.lilico.manager.config.NftCollectionConfig
import io.outblock.lilico.utils.NETWORK_MAINNET
import io.outblock.lilico.utils.NETWORK_SANDBOX
import io.outblock.lilico.utils.NETWORK_TESTNET
import io.outblock.lilico.utils.cpuScope
import io.outblock.lilico.utils.getChainNetworkPreference
import io.outblock.lilico.utils.isDev
import io.outblock.lilico.utils.isDeveloperModeEnable
import io.outblock.lilico.utils.logd
import io.outblock.lilico.utils.uiScope

private var network = if (isDev()) NETWORK_TESTNET else NETWORK_MAINNET
private var isDeveloperMode = false

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