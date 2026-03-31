package com.flowfoundation.wallet.network

import com.flowfoundation.wallet.manager.app.isTestnet

/**
 * Build explorer redirect URL via /api/v4/explorer endpoint.
 * The server returns a 302 redirect to the appropriate block explorer.
 *
 * @param id       Transaction hash, address, or contract identifier
 * @param type     "tx", "address", "account", "contract", "token"
 * @param chain    "flow" or "evm"
 * @param network  Optional, defaults to current network environment
 */
fun explorerUrl(
    id: String,
    type: String = "tx",
    chain: String = "flow",
    network: String? = null
): String {
    val net = network ?: if (isTestnet()) "testnet" else "mainnet"
    return "https://web.api.wallet.flow.com/api/v4/explorer?chain=$chain&network=$net&type=$type&id=$id"
}
