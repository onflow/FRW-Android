package com.flowfoundation.wallet.manager.account

import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.cache.CacheManager
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.coin.TokenStateManager
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.wallet.Wallet
import com.flowfoundation.wallet.wallet.AccountManager as FlowAccountManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.retrofitApi
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.uiScope
import java.lang.ref.WeakReference
import java.math.BigDecimal
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BalanceManager {
    private val TAG = BalanceManager::class.java.simpleName

    private val listeners = CopyOnWriteArrayList<WeakReference<OnBalanceUpdate>>()
    private val balanceList = CopyOnWriteArrayList<Balance>()
    private val cache by lazy { CacheManager("BALANCE_CACHE_v1.0", BalanceCache::class.java) }

    // New Flow Wallet Kit SDK instances
    private val wallet = Wallet()
    private val accountManager = FlowAccountManager(wallet)

    private val _balance = MutableStateFlow<BigDecimal>(BigDecimal.ZERO)
    val balance: StateFlow<BigDecimal> = _balance.asStateFlow()

    fun reload() {
        ioScope {
            balanceList.clear()
            balanceList.addAll(cache.read()?.data ?: emptyList())
        }
    }

    fun refresh() {
        if (WalletManager.isEVMAccountSelected()) {
            FlowCoinListManager.getFlowCoin()?.let {
                fetch(it)
            }
            getEVMTokenBalance()
        } else {
            fetchTokenBalance()
        }
    }

    private fun fetchTokenBalance() {
        ioScope {
            try {
                val coinList = FlowCoinListManager.coinList().filter { TokenStateManager.isTokenAdded(it) }
                val accounts = accountManager.accounts.first()
                val currentAccount = accounts.values.flatten().firstOrNull() ?: return@ioScope

                coinList.forEach { coin ->
                    balanceList.firstOrNull { it.isSameCoin(coin) }?.let { dispatchListeners(coin, it.balance) }

                    val balance = if (coin.isFlowCoin()) {
                        currentAccount.balance
                    } else {
                        wallet.getTokenBalance(currentAccount, coin.address)
                    }

                    val existBalance = balanceList.firstOrNull { it.isSameCoin(coin) }
                    val isDiff = balanceList.isEmpty() || existBalance == null || existBalance.balance != balance
                    if (isDiff) {
                        dispatchListeners(coin, balance)
                        balanceList.removeAll { it.isSameCoin(coin) }
                        balanceList.add(Balance(balance, coin.address, coin.contractName()))
                        ioScope { cache.cache(BalanceCache(balanceList.toList())) }
                    }
                }
            } catch (e: Exception) {
                logd(TAG, "Error fetching token balance: ${e.message}")
            }
        }
    }

    private fun getEVMTokenBalance() {
        ioScope {
            val address = EVMWalletManager.getEVMAddress() ?: return@ioScope
            val apiService = retrofitApi().create(ApiService::class.java)
            val balanceResponse = apiService.getEVMTokenBalance(address, chainNetWorkString())
            balanceResponse.data?.forEach { tokenBalance ->
                balanceList.firstOrNull { it.isSameEVMCoin(tokenBalance.address) }?.let {
                    FlowCoinListManager.getEVMCoin(it.address.orEmpty())?.let { coin ->
                        dispatchListeners(coin, it.balance)
                    }
                }
                if (tokenBalance.balance.isBlank()) {
                    return@forEach
                }
                val amountValue = tokenBalance.balance.toBigDecimal()
                val value = amountValue.movePointLeft(tokenBalance.decimal)
                val existBalance = balanceList.firstOrNull { listItem -> listItem.isSameEVMCoin(tokenBalance.address) }
                val isDiff = balanceList.isEmpty() || existBalance == null || existBalance.balance != value
                if (isDiff) {
                    FlowCoinListManager.getEVMCoin(tokenBalance.address)?.let { coin ->
                        dispatchListeners(coin, value)
                        balanceList.removeAll { listItem -> listItem.isSameCoin(coin) }
                        balanceList.add(Balance(value, coin.address, coin.contractName()))
                        ioScope { cache.cache(BalanceCache(balanceList.toList())) }
                    }
                }
            }
        }
    }

    private suspend fun getEVMBalanceByCoin(tokenAddress: String): BigDecimal {
        val address = EVMWalletManager.getEVMAddress() ?: return BigDecimal.ZERO
        return getEVMBalanceByCoin(tokenAddress, address)
    }

    private suspend fun getEVMBalanceByCoin(tokenAddress: String, evmAddress: String): BigDecimal {
        val apiService = retrofitApi().create(ApiService::class.java)
        val balanceResponse = apiService.getEVMTokenBalance(evmAddress, chainNetWorkString())
        val evmBalance = balanceResponse.data?.firstOrNull { it.address.equals(tokenAddress, true) } ?: return BigDecimal.ZERO
        return evmBalance.balance.toBigDecimal().movePointLeft(evmBalance.decimal)
    }

    fun getBalanceByCoin(coin: FlowCoin) {
        logd(TAG, "getBalanceByCoin:${coin.symbol}")
        fetch(coin)
    }

    fun addListener(callback: OnBalanceUpdate) {
        if (listeners.firstOrNull { it.get() == callback } != null) {
            return
        }
        uiScope { this.listeners.add(WeakReference(callback)) }
    }

    private fun dispatchListeners(coin: FlowCoin, balance: BigDecimal) {
        logd(TAG, "dispatchListeners ${coin.symbol}:$balance")
        uiScope {
            listeners.removeAll { it.get() == null }
            listeners.forEach { it.get()?.onBalanceUpdate(coin, Balance(balance, coin.address, coin.contractName())) }
        }
    }

    private fun fetch(coin: FlowCoin) {
        ioScope {
            try {
                balanceList.firstOrNull { it.isSameCoin(coin) }?.let { dispatchListeners(coin, it.balance) }

                val accounts = accountManager.accounts.first()
                val currentAccount = accounts.values.flatten().firstOrNull() ?: return@ioScope

                val balance = if (WalletManager.isEVMAccountSelected()) {
                    if (coin.isFlowCoin()) {
                        currentAccount.balance
                    } else {
                        getEVMBalanceByCoin(coin.address)
                    }
                } else {
                    if (coin.isFlowCoin()) {
                        currentAccount.balance
                    } else {
                        wallet.getTokenBalance(currentAccount, coin.address)
                    }
                }

                val existBalance = balanceList.firstOrNull { it.isSameCoin(coin) }
                val isDiff = balanceList.isEmpty() || existBalance == null || existBalance.balance != balance
                if (isDiff) {
                    dispatchListeners(coin, balance)
                    balanceList.removeAll { it.isSameCoin(coin) }
                    balanceList.add(Balance(balance, coin.address, coin.contractName()))
                    ioScope { cache.cache(BalanceCache(balanceList.toList())) }
                }
            } catch (e: Exception) {
                logd(TAG, "Error fetching balance: ${e.message}")
            }
        }
    }

    fun clear() {
        balanceList.clear()
        cache.clear()
    }

    suspend fun fetchBalance(): BigDecimal {
        return withContext(Dispatchers.IO) {
            try {
                val accounts = accountManager.accounts.first()
                val currentAccount = accounts.values.flatten().firstOrNull()
                
                if (currentAccount != null) {
                    val balance = currentAccount.balance
                    _balance.value = balance
                    logd(TAG, "Fetched balance: $balance")
                    balance
                } else {
                    loge(TAG, "No current account found")
                    BigDecimal.ZERO
                }
            } catch (e: Exception) {
                loge(TAG, "Error fetching balance: ${e.message}")
                BigDecimal.ZERO
            }
        }
    }

    suspend fun getBalanceForAddress(address: String): BigDecimal {
        return withContext(Dispatchers.IO) {
            try {
                val accounts = accountManager.accounts.first()
                val account = accounts.values.flatten().find { it.address == address }
                account?.balance ?: BigDecimal.ZERO
            } catch (e: Exception) {
                loge(TAG, "Error getting balance for address $address: ${e.message}")
                BigDecimal.ZERO
            }
        }
    }

    suspend fun getBalanceForNetwork(network: String): BigDecimal {
        return withContext(Dispatchers.IO) {
            try {
                val accounts = accountManager.accounts.first()
                val networkAccounts = accounts.values.flatten().filter { it.network == network }
                networkAccounts.sumOf { it.balance }
            } catch (e: Exception) {
                loge(TAG, "Error getting balance for network $network: ${e.message}")
                BigDecimal.ZERO
            }
        }
    }

    suspend fun refreshBalance() {
        fetchBalance()
    }
}

interface OnBalanceUpdate {
    fun onBalanceUpdate(coin: FlowCoin, balance: Balance)
}

data class Balance(
    @SerializedName("balance")
    val balance: BigDecimal,
    @SerializedName("address")
    val address: String? = "",
    @SerializedName("contractName")
    val contractName: String? = ""
) {
    fun isSameCoin(coin: FlowCoin): Boolean {
        return address.equals(coin.address, ignoreCase = true)
                && contractName.equals(coin.contractName(), ignoreCase = true)
    }

    fun isSameEVMCoin(tokenAddress: String): Boolean {
        return address.equals(tokenAddress, ignoreCase = true)
    }
}

private class BalanceCache(
    @SerializedName("data")
    val data: List<Balance>,
)

