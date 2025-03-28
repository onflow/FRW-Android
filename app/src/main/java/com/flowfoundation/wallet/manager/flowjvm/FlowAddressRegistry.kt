package com.flowfoundation.wallet.manager.flowjvm

import org.onflow.flow.AddressRegistry
import org.onflow.flow.ChainId
import com.nftco.flow.sdk.FlowAddress
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
                    NETWORK_MAINNET -> ChainId.Mainnet
                    NETWORK_TESTNET -> ChainId.Testnet
                    else -> ChainId.Mainnet
                }
            )
        }
    }

    fun addressRegistry() = AddressRegistry().apply {
        register(NETWORK_MAINNET)
        register(NETWORK_TESTNET)
    }
}