package com.flowfoundation.wallet.manager.token

import com.flowfoundation.wallet.cache.DisplayTokenCacheManager
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.CadenceScript
import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.profile.subpage.currency.model.selectedCurrency
import com.flowfoundation.wallet.page.token.list.CadenceTokenListProvider
import com.flowfoundation.wallet.page.token.list.EVMTokenListProvider
import com.flowfoundation.wallet.page.token.list.TokenListProvider
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.lang.ref.WeakReference
import java.math.BigDecimal
import java.util.concurrent.CopyOnWriteArrayList

object FungibleTokenListManager {
    private val TAG = FungibleTokenListManager::class.java.simpleName

    private val tokenListCache = mutableMapOf<String, DisplayTokenListCache>()
    private val currentDisplayTokenList = CopyOnWriteArrayList<FungibleToken>()
    private var currentTokenProvider: TokenListProvider? = null

    private val listeners = CopyOnWriteArrayList<WeakReference<FungibleTokenListUpdateListener>>()
    private val tokenUpdateListeners = CopyOnWriteArrayList<WeakReference<FungibleTokenUpdateListener>>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun init() {
        tokenListCache.clear()
        tokenListCache.putAll(DisplayTokenCacheManager.read() ?: emptyMap())
    }

    fun addTokenListUpdateListener(listener: FungibleTokenListUpdateListener) {
        if (listeners.firstOrNull { it.get() == listener } != null) {
            return
        }
        uiScope {
            this.listeners.add(WeakReference(listener))
        }
    }

    fun addTokenUpdateListener(listener: FungibleTokenUpdateListener) {
        if (tokenUpdateListeners.firstOrNull { it.get() == listener } != null) {
            return
        }
        uiScope {
            this.tokenUpdateListeners.add(WeakReference(listener))
        }
    }

    private fun dispatchListeners(token: FungibleToken) {
        uiScope {
            tokenUpdateListeners.removeAll { it.get() == null }
            tokenUpdateListeners.forEach { it.get()?.onTokenUpdated(token) }
        }
    }

    private fun dispatchDisplayUpdated(token: FungibleToken, isAdd: Boolean) {
        uiScope {
            listeners.removeAll { it.get() == null}
            listeners.forEach { it.get()?.onTokenDisplayUpdated(token, isAdd) }
        }
    }

    private fun dispatchListeners() {
        val listSnapshot = currentDisplayTokenList.toList()
        logd(TAG, "dispatchTokenListUpdate::${listSnapshot}")
        uiScope {
            listeners.removeAll { it.get() == null}
            listeners.forEach { it.get()?.onTokenListUpdated(listSnapshot) }
        }
    }

    private fun getProvider(address: String): TokenListProvider {

        val isEVMForAddress = EVMWalletManager.isEVMWalletAddress(address)
        val existingProvider = currentTokenProvider

        if (existingProvider != null) {
            val providerCurrentAddress = existingProvider.getWalletAddress()

            if (providerCurrentAddress == address) {
                if (isEVMForAddress) {
                    if (existingProvider is EVMTokenListProvider) {
                        logd(TAG, "Reusing EVMTokenListProvider for address: $address")
                        return existingProvider
                    } else {
                        logd(TAG, "Address matches ($address), target is EVM, but current provider is ${existingProvider::class.simpleName}. Creating new EVMTokenListProvider.")
                        return EVMTokenListProvider(address)
                    }
                } else {
                    if (existingProvider is CadenceTokenListProvider) {
                        logd(TAG, "Reusing CadenceTokenListProvider for address: $address")
                        return existingProvider
                    } else {
                        logd(TAG, "Address matches ($address), target is Cadence, but current provider is ${existingProvider::class.simpleName}. Creating new CadenceTokenListProvider.")
                        return CadenceTokenListProvider(address)
                    }
                }
            } else {
                logd(TAG, "Provider exists for address $providerCurrentAddress, but requested for $address. Creating new provider.")
                return if (isEVMForAddress) {
                    EVMTokenListProvider(address)
                } else {
                    CadenceTokenListProvider(address)
                }
            }
        } else {
            logd(TAG, "No current provider. Creating new provider for $address.")
            return if (isEVMForAddress) {
                EVMTokenListProvider(address)
            } else {
                CadenceTokenListProvider(address)
            }
        }
    }

    fun reload() {
        scope.launch {
            val address = WalletManager.selectedWalletAddress()
            if (address.isBlank()) {
                currentDisplayTokenList.clear()
                dispatchListeners()
                logd(TAG, "No selected wallet address, token list cleared.")
                return@launch
            }

            tokenListCache[address]?.let { cachedList ->
                if (cachedList.displayTokenList.isNotEmpty()) {
                    currentDisplayTokenList.clear()
                    currentDisplayTokenList.addAll(cachedList.displayTokenList.distinctBy { it.contractId() })
                    dispatchListeners()
                    logd(TAG, "Loaded token list from cache for address: $address")
                }
            }

            updateTokenList(address)
        }
    }

    suspend fun updateTokenInfo(contractId: String) {
        val address = WalletManager.selectedWalletAddress()
        val provider = getProvider(address)
        currentTokenProvider = provider
        try {
            val freshList = provider.getTokenList(address)
            freshList.firstOrNull { it.isSameToken(contractId) }?.let {
                dispatchListeners(it)
            }
        } catch (e: Exception) {
            loge(TAG, e)
        }
    }

    suspend fun updateTokenList(address: String = WalletManager.selectedWalletAddress(), contractId: String? = null) {
        val provider = getProvider(address)
        currentTokenProvider = provider

        try {
            val currency = selectedCurrency()
            val network = chainNetWorkString()
            logd(TAG, "Fetching token list for address: $address, currency: ${currency.name}, network: $network")

            val freshList = provider.getTokenList(address, currency, network)

            if (currentDisplayTokenList.isEmpty()) {
                currentDisplayTokenList.addAll(freshList.distinctBy { it.contractId() })
                updateDisplayTokenListCache(address)
                dispatchListeners()
                logd(TAG, "Initial load from provider for token list for address: $address. Count: ${currentDisplayTokenList.size}")
                return
            }
            val freshTokensMap = freshList.associateBy { it.contractId() }

            val updatedFinalTokens = mutableListOf<FungibleToken>()
            for (existingToken in currentDisplayTokenList) {
                freshTokensMap[existingToken.contractId()]?.let { updatedVersionOfExistingToken ->
                    updatedFinalTokens.add(updatedVersionOfExistingToken)
                }
            }
            contractId?.let { targetId ->
                freshTokensMap[targetId]?.takeIf { freshToken ->
                    currentDisplayTokenList.none { it.contractId() == targetId }
                }?.let { newToken ->
                    updatedFinalTokens.add(newToken)
                    logd(TAG, "Added new token by contractId: $targetId")
                }
            }

            currentDisplayTokenList.clear()
            currentDisplayTokenList.addAll(updatedFinalTokens.distinctBy { it.contractId() })
            updateDisplayTokenListCache(address)
            logd(TAG, "Successfully updated token list for address: $address. Count: ${currentDisplayTokenList.size}")
            dispatchListeners()
        } catch (e: Exception) {
            loge("Error reloading token list for address: $address", e)
        }
    }

    private fun updateDisplayTokenListCache(address: String) {
        val oldItem = tokenListCache[address]
        if (oldItem != null) {
            tokenListCache[address] = oldItem.copy(displayTokenList = ArrayList(currentDisplayTokenList))
        } else {
            tokenListCache[address] = DisplayTokenListCache(displayTokenList = ArrayList(currentDisplayTokenList))
        }
        DisplayTokenCacheManager.cache(tokenListCache)
    }

    fun isHideDustTokens(): Boolean {
        return tokenListCache[WalletManager.selectedWalletAddress()]?.hideDustTokens ?: false
    }

    fun isOnlyShowVerifiedTokens(): Boolean {
        return tokenListCache[WalletManager.selectedWalletAddress()]?.onlyShowVerifiedTokens ?: false
    }

    fun setHideDustTokens(hide: Boolean) {
        val address = WalletManager.selectedWalletAddress()
        if (address.isBlank()) {
            return
        }
        val oldItem = tokenListCache[address] ?: DisplayTokenListCache()
        if (hide) {
            val filteredList = currentDisplayTokenList.filter { it.tokenBalanceInUSD() > BigDecimal(0.01) }
            currentDisplayTokenList.clear()
            currentDisplayTokenList.addAll(filteredList)
            tokenListCache[address] = oldItem.copy(hideDustTokens = true, displayTokenList = filteredList)
        } else {
            val tokenList = getCurrentTokenListSnapshot()
            val displayListSnapshot = currentDisplayTokenList.toList()
            currentDisplayTokenList.clear()
            if (isOnlyShowVerifiedTokens()) {
                val filteredList = tokenList.filter { it.isVerified }.filter { verified -> displayListSnapshot.any { it.isSameToken(verified.contractId()) } }
                currentDisplayTokenList.addAll(filteredList)
            } else {
                currentDisplayTokenList.addAll(tokenList)
            }
            tokenListCache[address] =
                oldItem.copy(hideDustTokens = false, displayTokenList = currentDisplayTokenList)
        }
        DisplayTokenCacheManager.cache(tokenListCache)
        dispatchListeners()
    }

    fun setOnlyShowVerifiedTokens(show: Boolean) {
        val address = WalletManager.selectedWalletAddress()
        if (address.isBlank()) {
            return
        }
        val oldItem = tokenListCache[address] ?: DisplayTokenListCache()
        if (show) {
            val filteredList = currentDisplayTokenList.filter { it.isVerified }
            currentDisplayTokenList.clear()
            currentDisplayTokenList.addAll(filteredList)
            tokenListCache[address] =
                oldItem.copy(onlyShowVerifiedTokens = true, displayTokenList = filteredList)
        } else {
            val tokenList = getCurrentTokenListSnapshot()
            val displayListSnapshot = currentDisplayTokenList.toList()
            currentDisplayTokenList.clear()
            if (isHideDustTokens()) {
                val filteredList = tokenList.filter { it.tokenBalanceInUSD() > BigDecimal(0.01) }.filter { verified -> displayListSnapshot.any{ it.isSameToken(verified.contractId()) } }
                currentDisplayTokenList.addAll(filteredList)
            } else {
                currentDisplayTokenList.addAll(tokenList)
            }
            tokenListCache[address] = oldItem.copy(onlyShowVerifiedTokens = false, displayTokenList = currentDisplayTokenList)
        }
        DisplayTokenCacheManager.cache(tokenListCache)
        dispatchListeners()
    }

    fun getCurrentDisplayTokenListSnapshot(): List<FungibleToken> {
        return currentDisplayTokenList.toList()
    }

    fun getCurrentTokenListSnapshot(): List<FungibleToken> {
        return currentTokenProvider?.getFungibleTokenListSnapshot() ?: emptyList()
    }

    fun getFungibleToken(predicate: (FungibleToken) -> Boolean): FungibleToken? {
        return getCurrentTokenListSnapshot().firstOrNull(predicate)
    }

    fun addCustomToken() {
        currentTokenProvider?.run {
            addCustomToken()
            ioScope {
                updateTokenList(WalletManager.selectedWalletAddress())
            }
        }
    }

    fun deleteCustomToken(contractAddress: String) {
        currentTokenProvider?.run {
            deleteCustomToken(contractAddress)
            ioScope {
                updateTokenList(WalletManager.selectedWalletAddress())
            }
        }
    }

    fun isFlowToken(contractId: String) = getCurrentTokenListSnapshot().any { it.isFlowToken() && it.contractId().equals(contractId, true) }

    fun getFlowToken() = currentTokenProvider?.getFlowToken()

    fun getFlowTokenContractId() = currentTokenProvider?.getFlowTokenContractId().orEmpty()

    fun getTokenById(contractId: String) = currentTokenProvider?.getTokenById(contractId)

    fun isDisplayToken(contractId: String) = getCurrentDisplayTokenListSnapshot().any { it.isSameToken(contractId) }

    fun isTokenAdded(contractId: String) = getCurrentTokenListSnapshot().any { it.isSameToken(contractId) }

    fun addDisplayToken(token: FungibleToken) {
        ioScope {
            if (currentDisplayTokenList.any { it.isSameToken(token.contractId()) }) {
                logd(TAG, "Token ${token.contractId()} already in display list.")
                return@ioScope
            }
            currentDisplayTokenList.add(token)
            val address = WalletManager.selectedWalletAddress()
            if (address.isNotBlank()) {
                updateDisplayTokenListCache(address)
            }
            dispatchDisplayUpdated(token, true)
            logd(TAG, "Added token ${token.contractId()} to display list. New count: ${currentDisplayTokenList.size}")
        }
    }

    fun removeDisplayToken(token: FungibleToken) {
        ioScope {
            val removed = currentDisplayTokenList.removeAll { it.isSameToken(token.contractId()) }
            if (removed) {
                val address = WalletManager.selectedWalletAddress()
                if (address.isNotBlank()) {
                    updateDisplayTokenListCache(address)
                }
                dispatchDisplayUpdated(token, false)
                logd(TAG, "Removed token ${token.contractId()} from display list. New count: ${currentDisplayTokenList.size}")
            } else {
                logd(TAG, "Token ${token.contractId()} not found in display list for removal.")
            }
        }
    }

    fun clear() {
        currentDisplayTokenList.clear()
    }
}

interface FungibleTokenListUpdateListener {
    fun onTokenListUpdated(list: List<FungibleToken>)
    fun onTokenDisplayUpdated(token: FungibleToken, isAdd: Boolean)
}

interface FungibleTokenUpdateListener {
    fun onTokenUpdated(token: FungibleToken)
}


fun FungibleToken.formatCadence(cadenceScript: CadenceScript): String {
    return cadenceScript.getScript().replace("<Token>", tokenContractName())
        .replace("<TokenAddress>", tokenAddress())
        .replace("<TokenReceiverPath>", flowReceiverPath ?: "")
        .replace("<TokenBalancePath>", flowBalancePath ?: "")
        .replace("<TokenStoragePath>", flowStoragePath ?: "")
} 