package com.flowfoundation.wallet.manager.account

import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.cache.CacheManager
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.coin.TokenStateManager
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryCOATokenBalance
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryTokenBalance
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryTokenListBalance
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

    private val cache by lazy { CacheManager("BALANCE_CACHE_v1.0", BalanceCache::class.java) }

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
            val coinList = FlowCoinListManager.coinList().filter { TokenStateManager.isTokenAdded(it) }
            val balanceMap = cadenceQueryTokenListBalance() ?: return@ioScope
            coinList.forEach { coin ->
                balanceList.firstOrNull { it.isSameCoin(coin) }?.let { dispatchListeners(coin, it.balance) }

                val balance = balanceMap[coin.contractId()] ?: BigDecimal.ZERO

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

    fun getBalanceByCoin(coinContractId: String) {
        val coin = FlowCoinListManager.getCoinById(coinContractId) ?: return
        getBalanceByCoin(coin)
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
                if (coin.isFlowCoin()) {
                    cadenceQueryCOATokenBalance()
                } else {
                    getEVMBalanceByCoin(coin.address)
                }
            } else {
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

    fun isSameEVMCoin(address: String): Boolean {
        return address.equals(address, ignoreCase = true)
    }
}

private class BalanceCache(
    @SerializedName("data")
    val data: List<Balance>,
)

