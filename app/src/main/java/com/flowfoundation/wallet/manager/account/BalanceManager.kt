package com.flowfoundation.wallet.manager.account

import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.cache.CacheManager
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.coin.TokenStateManager
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceGetTokenBalanceStorage
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryCOATokenBalance
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryTokenBalance
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryTokenBalanceWithAddress
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.retrofitApi
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import java.lang.ref.WeakReference
import java.math.BigDecimal
import java.util.concurrent.CopyOnWriteArrayList

object BalanceManager {
    private val TAG = BalanceManager::class.java.simpleName

    private val listeners = CopyOnWriteArrayList<WeakReference<OnBalanceUpdate>>()

    private val balanceList = CopyOnWriteArrayList<Balance>()

    private val cache by lazy { CacheManager<BalanceCache>("BALANCE_CACHE_v1.0", BalanceCache::class.java) }

    fun reload() {
        ioScope {
            balanceList.clear()
            balanceList.addAll(cache.read()?.data ?: emptyList())
        }
    }

    fun refresh() {
        logd(TAG, "refresh() called")
        if (WalletManager.isEVMAccountSelected()) {
            logd(TAG, "EVM account selected, fetching EVM balances")
            FlowCoinListManager.getFlowCoin()?.let {
                fetch(it)
            }
            getEVMTokenBalance()
        } else if (WalletManager.isChildAccountSelected()) {
            logd(TAG, "Child account selected, fetching child account balances")
            fetchChildAccountTokenBalance()
        } else {
            logd(TAG, "Regular account selected, fetching regular balances")
            fetchTokenBalance()
        }
    }

    private fun fetchTokenBalance() {
        ioScope {
            val coinList = FlowCoinListManager.coinList().filter { TokenStateManager.isTokenAdded(it) }
            val balanceMap = cadenceGetTokenBalanceStorage() ?: return@ioScope
            coinList.forEach { coin ->
                balanceList.firstOrNull { it.isSameCoin(coin) }?.let { dispatchListeners(coin, it.balance) }

                //todo available flow balance
                val balance = if (coin.isFlowCoin()) {
                    balanceMap["availableFlowToken"] ?: BigDecimal.ZERO
                } else {
                    balanceMap[coin.getFTIdentifier()] ?: BigDecimal.ZERO
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
            balanceList.firstOrNull { it.isSameCoin(coin) }?.let { dispatchListeners(coin, it.balance) }

            val balance = if (WalletManager.isEVMAccountSelected()) {
                logd(TAG, "Fetching balance for EVM account")
                if (coin.isFlowCoin()) {
                    cadenceQueryCOATokenBalance()
                } else {
                    getEVMBalanceByCoin(coin.address)
                }
            } else if (WalletManager.isChildAccountSelected()) {
                logd(TAG, "Fetching balance for child account")
                val selectedAddress = WalletManager.selectedWalletAddress()
                cadenceQueryTokenBalanceWithAddress(coin, selectedAddress)
            } else {
                logd(TAG, "Fetching balance for regular account")
                if (coin.isFlowCoin()) {
                    AccountInfoManager.getCurrentFlowBalance() ?: cadenceQueryTokenBalance(coin)
                } else {
                    cadenceQueryTokenBalance(coin)
                }
            }
            if (balance != null) {
                val existBalance = balanceList.firstOrNull { it.isSameCoin(coin) }
                val isDiff = balanceList.isEmpty() || existBalance == null || existBalance.balance != balance
                if (isDiff) {
                    dispatchListeners(coin, balance)
                    balanceList.removeAll { it.isSameCoin(coin) }
                    balanceList.add(Balance(balance, coin.address, coin.contractName()))
                    ioScope { cache.cache(BalanceCache(balanceList.toList())) }
                }
            }
        }
    }

    private fun fetchChildAccountTokenBalance() {
        ioScope {
            val selectedAddress = WalletManager.selectedWalletAddress()
            logd(TAG, "fetchChildAccountTokenBalance for address: '$selectedAddress'")
            
            val coinList = FlowCoinListManager.coinList().filter { TokenStateManager.isTokenAdded(it) }
            logd(TAG, "Fetching balances for ${coinList.size} coins")
            
            coinList.forEach { coin ->
                logd(TAG, "Fetching balance for coin: ${coin.symbol}")
                
                // Dispatch cached balance first if available
                balanceList.firstOrNull { it.isSameCoin(coin) }?.let { 
                    logd(TAG, "Found cached balance for ${coin.symbol}: ${it.balance}")
                    dispatchListeners(coin, it.balance) 
                }

                val balance = if (coin.isFlowCoin()) {
                    logd(TAG, "Fetching Flow coin balance for child account")
                    cadenceQueryTokenBalanceWithAddress(coin, selectedAddress)
                } else {
                    logd(TAG, "Fetching ${coin.symbol} balance for child account")
                    cadenceQueryTokenBalanceWithAddress(coin, selectedAddress)
                }
                
                logd(TAG, "Fetched balance for ${coin.symbol}: $balance")
                
                if (balance != null) {
                    val existBalance = balanceList.firstOrNull { it.isSameCoin(coin) }
                    val isDiff = balanceList.isEmpty() || existBalance == null || existBalance.balance != balance
                    if (isDiff) {
                        logd(TAG, "Balance changed for ${coin.symbol}, updating: $balance")
                        dispatchListeners(coin, balance)
                        balanceList.removeAll { it.isSameCoin(coin) }
                        balanceList.add(Balance(balance, coin.address, coin.contractName()))
                        ioScope { cache.cache(BalanceCache(balanceList.toList())) }
                    } else {
                        logd(TAG, "Balance unchanged for ${coin.symbol}: $balance")
                    }
                } else {
                    logd(TAG, "Failed to fetch balance for ${coin.symbol}")
                }
            }
        }
    }

    fun clear() {
        balanceList.clear()
        cache.clear()
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

