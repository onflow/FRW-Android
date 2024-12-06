package com.flowfoundation.wallet.manager.coin

import com.flowfoundation.wallet.cache.CustomTokenCacheManager.cacheSync
import com.flowfoundation.wallet.cache.CustomTokenCacheManager.read
import com.flowfoundation.wallet.manager.app.networkChainId
import com.flowfoundation.wallet.page.token.custom.model.CustomTokenItem
import com.flowfoundation.wallet.utils.logd

object CustomTokenManager {
    private val TAG = CustomTokenManager::class.java.simpleName

    private val cacheList = mutableListOf<CustomTokenItem>()

    fun init() {
        cacheList.clear()
        cacheList.addAll(read() ?: emptyList())
    }

    fun isCustomToken(contractAddress: String): Boolean {
        return cacheList.any { contractAddress.equals(it.contractAddress, true) }
    }

    fun getCurrentCustomTokenList(): List<CustomTokenItem> {
        return cacheList.filter { it.chainId == networkChainId() && it.isWalletTokenType() }.toList()
    }

    fun addEVMCustomToken(tokenItem: CustomTokenItem) {
        if (cacheList.any { it.isSameToken(tokenItem.chainId, tokenItem.contractAddress) }) {
            logd(TAG, "already add this token :: ${tokenItem.symbol} :: ${tokenItem.contractAddress} :: in ${tokenItem.chainId} network")
            return
        }
        cacheList.add(tokenItem)
        FlowCoinListManager.addCustomToken()
        TokenStateManager.customTokenStateChanged(tokenItem, isAdded = true)
        cacheSync(cacheList)
    }

    fun deleteCustomToken(coin: FlowCoin) {
        val customToken = cacheList.firstOrNull { it.isSameToken(coin.chainId, coin.address) }
        if (customToken == null) {
            logd(TAG, "can not find this custom token :: ${coin.symbol} :: in ${coin.chainId} " +
                    "network")
            return
        }
        cacheList.remove(customToken)
        FlowCoinListManager.deleteCustomToken(customToken.contractAddress)
        TokenStateManager.customTokenStateChanged(customToken, isAdded = false)
        cacheSync(cacheList)
    }
}
