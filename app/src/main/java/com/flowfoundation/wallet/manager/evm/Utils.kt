package com.flowfoundation.wallet.manager.evm

import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.Env

const val PREVIEWNET_CHAIN_ID = 646
const val PREVIEWNET_RPC_URL = "https://previewnet.evm.nodes.onflow.org"

fun loadInitJS(): String {
    return """
        (function() {
            var config = {                
                ethereum: {
                    chainId: $PREVIEWNET_CHAIN_ID,
                    rpcUrl: "$PREVIEWNET_RPC_URL"
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