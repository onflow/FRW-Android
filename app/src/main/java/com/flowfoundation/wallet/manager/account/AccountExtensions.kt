package com.flowfoundation.wallet.manager.account

import com.flowfoundation.wallet.utils.logd

// Helper extension function to get the Flow address for a specific network
fun Account.getFlowAddress(networkName: String, logTag: String = "AccountExtensions"): String? {
    logd(logTag, "Account.getFlowAddress for ${this.userInfo.username} on network $networkName")
    val foundAddress = this.wallet?.wallets?.asSequence()
        ?.flatMap { walletData ->
            walletData.blockchain?.asSequence() ?: emptySequence()
        }
        ?.find { blockchainData ->
            val matches = blockchainData.chainId == networkName

            matches
        }?.address
    logd(logTag, "  Returning address: $foundAddress for ${this.userInfo.username} on $networkName")
    return foundAddress
} 