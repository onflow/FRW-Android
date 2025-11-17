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
            // Compare case-insensitively since chainId from backend might be "mainnet" but networkName might be "Mainnet"
            val matches = blockchainData.chainId?.lowercase() == networkName.lowercase()
            matches
        }?.address
    logd(logTag, "  Returning address: $foundAddress for ${this.userInfo.username} on $networkName")
    return foundAddress
} 