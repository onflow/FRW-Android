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

        val isEVMForAddress = EVMWalletManager.isEVMWalletAddress(address) || EVMWalletManager.isEOAAddress(address)
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
            val hiddenIds = getHiddenTokenIds(address)

            val visibleTokens = freshList.filter { it.contractId() !in hiddenIds }
            val filteredList = applyFilters(visibleTokens.distinctBy { it.contractId() })

            currentDisplayTokenList.clear()
            currentDisplayTokenList.addAll(filteredList)
            logd(TAG, "Updated token list for address: $address. Count: ${currentDisplayTokenList.size}, hidden: ${hiddenIds.size}")
            dispatchListeners()
        } catch (e: Exception) {
            loge("Error reloading token list for address: $address", e)
        }
    }

    private fun updateDisplayTokenListCache(address: String) {
        DisplayTokenCacheManager.cache(tokenListCache)
    }

    private fun getHiddenTokenIds(address: String = WalletManager.selectedWalletAddress()): Set<String> {
        return tokenListCache[address]?.hiddenTokenIds ?: emptySet()
    }

    private fun rebuildDisplayList(address: String) {
        val allTokens = getCurrentTokenListSnapshot()
        val hiddenIds = getHiddenTokenIds(address)
        val visibleTokens = allTokens.filter { it.contractId() !in hiddenIds }
        val filteredList = applyFilters(visibleTokens.distinctBy { it.contractId() })
        currentDisplayTokenList.clear()
        currentDisplayTokenList.addAll(filteredList)
    }

    private fun applyFilters(tokens: List<FungibleToken>): List<FungibleToken> {
        var filteredList = tokens

        if (isHideDustTokens()) {
            filteredList = filteredList.filter { it.tokenBalanceInUSD() > BigDecimal(0.01) }
        }

        if (isOnlyShowVerifiedTokens()) {
            filteredList = filteredList.filter { it.isVerified }
        }

        return filteredList
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
        logd(TAG, "setHideDustTokens: hide=$hide")
        val oldItem = tokenListCache[address] ?: DisplayTokenListCache()
        tokenListCache[address] = oldItem.copy(hideDustTokens = hide)
        rebuildDisplayList(address)
        DisplayTokenCacheManager.cache(tokenListCache)
        dispatchListeners()
    }

    fun setOnlyShowVerifiedTokens(show: Boolean) {
        val address = WalletManager.selectedWalletAddress()
        if (address.isBlank()) {
            return
        }
        val oldItem = tokenListCache[address] ?: DisplayTokenListCache()
        tokenListCache[address] = oldItem.copy(onlyShowVerifiedTokens = show)
        rebuildDisplayList(address)
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

    fun showToken(token: FungibleToken) {
        ioScope {
            val address = WalletManager.selectedWalletAddress()
            if (address.isBlank()) return@ioScope

            val oldItem = tokenListCache[address] ?: DisplayTokenListCache()
            val newHidden = oldItem.hiddenTokenIds - token.contractId()
            tokenListCache[address] = oldItem.copy(hiddenTokenIds = newHidden)

            if (currentDisplayTokenList.none { it.isSameToken(token.contractId()) }) {
                val shouldShow = applyFilters(listOf(token)).isNotEmpty()
                if (shouldShow) {
                    currentDisplayTokenList.add(token)
                }
            }

            updateDisplayTokenListCache(address)
            dispatchDisplayUpdated(token, true)
            logd(TAG, "Showed token ${token.contractId()}. Hidden count: ${newHidden.size}")
        }
    }

    fun hideToken(token: FungibleToken) {
        ioScope {
            val address = WalletManager.selectedWalletAddress()
            if (address.isBlank()) return@ioScope

            val oldItem = tokenListCache[address] ?: DisplayTokenListCache()
            val newHidden = oldItem.hiddenTokenIds + token.contractId()
            tokenListCache[address] = oldItem.copy(hiddenTokenIds = newHidden)

            currentDisplayTokenList.removeAll { it.isSameToken(token.contractId()) }

            updateDisplayTokenListCache(address)
            dispatchDisplayUpdated(token, false)
            logd(TAG, "Hid token ${token.contractId()}. Hidden count: ${newHidden.size}")
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

@Serializable
data class DisplayTokenListCache(
    @SerializedName("hideDustTokens")
    val hideDustTokens: Boolean = false,
    @SerializedName("onlyShowVerifiedTokens")
    val onlyShowVerifiedTokens: Boolean = false,
    @SerializedName("hiddenTokenIds")
    val hiddenTokenIds: Set<String> = emptySet()
)
