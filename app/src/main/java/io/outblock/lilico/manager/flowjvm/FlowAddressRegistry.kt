package io.outblock.lilico.manager.flowjvm

import com.nftco.flow.sdk.AddressRegistry
import com.nftco.flow.sdk.FlowAddress
import com.nftco.flow.sdk.FlowChainId
import io.outblock.lilico.manager.config.AppConfig
import io.outblock.lilico.utils.NETWORK_MAINNET
import io.outblock.lilico.utils.NETWORK_SANDBOX
import io.outblock.lilico.utils.NETWORK_TESTNET
import io.outblock.lilico.utils.logw

internal class FlowAddressRegistry {

    private fun AddressRegistry.register(network: Int) {
        AppConfig.addressRegistry(network).forEach { (t, u) ->
            logw("FlowAddressRegistry", "register  name:$t,address:$u,network:${network}")
            register(
                t, FlowAddress(u), when (network) {
                    NETWORK_MAINNET -> FlowChainId.MAINNET
                    NETWORK_TESTNET -> FlowChainId.TESTNET
                    NETWORK_SANDBOX -> FlowChainId.SANDBOX
                    else -> FlowChainId.MAINNET
                }
            )
        }
    }

    fun addressRegistry() = AddressRegistry().apply {
        register(NETWORK_MAINNET)
        register(NETWORK_TESTNET)
//        register(NETWORK_SANDBOX)
    }
}