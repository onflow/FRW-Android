package com.flowfoundation.wallet.manager.account

import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.cache.CacheManager
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.coin.TokenStateManager
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.CADENCE_QUERY_COA_EVM_ADDRESS
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryCOATokenBalance
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryTokenBalance
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import java.lang.ref.WeakReference
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
        FlowCoinListManager.coinList().filter { TokenStateManager.isTokenAdded(it.address()) }.forEach { fetch(it) }
    }

    fun getBalanceByCoin(coin: FlowCoin) {
        logd(TAG, "getBalanceByCoin:${coin.symbol}")
        fetch(coin)
    }

    fun getBalanceByCoin(coinSymbol: String) {
        val coin = FlowCoinListManager.getCoin(coinSymbol) ?: return
        getBalanceByCoin(coin)
    }

    fun addListener(callback: OnBalanceUpdate) {
        if (listeners.firstOrNull { it.get() == callback } != null) {
            return
        }
        uiScope { this.listeners.add(WeakReference(callback)) }
    }

    private fun dispatchListeners(coin: FlowCoin, balance: Float) {
        logd(TAG, "dispatchListeners ${coin.symbol}:$balance")
        uiScope {
            listeners.removeAll { it.get() == null }
            listeners.forEach { it.get()?.onBalanceUpdate(coin, Balance(coin.symbol, balance)) }
        }
    }

    fun getBalanceList() = balanceList.toList()

    private fun fetch(coin: FlowCoin) {
        ioScope {
            balanceList.firstOrNull { it.symbol == coin.symbol }?.let { dispatchListeners(coin, it.balance) }

            val balance = if (WalletManager.isEVMAccountSelected()) {
                cadenceQueryCOATokenBalance()
            } else {
                cadenceQueryTokenBalance(coin)
            }
            if (balance != null) {
                val existBalance = balanceList.firstOrNull { coin.symbol == it.symbol }
                val isDiff = balanceList.isEmpty() || existBalance == null || existBalance.balance != balance
                if (isDiff) {
                    dispatchListeners(coin, balance)
                    balanceList.removeAll { it.symbol == coin.symbol }
                    balanceList.add(Balance(coin.symbol, balance))
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
    @SerializedName("symbol")
    val symbol: String,
    @SerializedName("balance")
    val balance: Float,
)

private class BalanceCache(
    @SerializedName("data")
    val data: List<Balance>,
)

