package com.flowfoundation.wallet.manager.flowjvm

import org.onflow.flow.sdk.AddressRegistry
import org.onflow.flow.sdk.FlowAddress
import org.onflow.flow.sdk.FlowChainId
import com.flowfoundation.wallet.manager.config.AppConfig
import com.flowfoundation.wallet.utils.NETWORK_MAINNET
import com.flowfoundation.wallet.utils.NETWORK_TESTNET
import com.flowfoundation.wallet.utils.logw

internal class FlowAddressRegistry {

    private fun AddressRegistry.register(network: Int) {
        AppConfig.addressRegistry(network).forEach { (t, u) ->
            logw("FlowAddressRegistry", "register  name:$t,address:$u,network:${network}")
            register(
                t, FlowAddress(u), when (network) {
                    NETWORK_MAINNET -> FlowChainId.MAINNET
                    NETWORK_TESTNET -> FlowChainId.TESTNET
                    else -> FlowChainId.MAINNET
                }
            )
        }
    }

    fun addressRegistry() = AddressRegistry().apply {
        register(NETWORK_MAINNET)
        register(NETWORK_TESTNET)
    }
}