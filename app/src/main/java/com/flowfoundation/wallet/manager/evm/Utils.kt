package com.flowfoundation.wallet.manager.evm

import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.Env

const val ETH_CHAIN_ID = 1
const val ETH_RPC_URL = "https://cloudflare-eth.com"

fun loadInitJS(): String {
    return """
        (function() {
            var config = {                
                ethereum: {
                    chainId: $ETH_CHAIN_ID,
                    rpcUrl: "$ETH_RPC_URL"
                },
                isDebug: true
            };
            trustwallet.ethereum = new trustwallet.Provider(config);
            trustwallet.postMessage = (json) => {
                window._tw_.postMessage(JSON.stringify(json));
            }
            window.ethereum = trustwallet.ethereum;
        })();
        """.trimIndent()
}

fun loadProviderJS(): String {
    return Env.getApp().resources.openRawResource(R.raw.trust_min).bufferedReader().use { it.readText() }
}