package io.outblock.lilico.page.wallet

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.outblock.lilico.manager.account.Balance
import io.outblock.lilico.manager.account.BalanceManager
import io.outblock.lilico.manager.account.OnBalanceUpdate
import io.outblock.lilico.manager.account.OnWalletDataUpdate
import io.outblock.lilico.manager.account.WalletFetcher
import io.outblock.lilico.manager.coin.CoinRateManager
import io.outblock.lilico.manager.coin.FlowCoin
import io.outblock.lilico.manager.coin.FlowCoinListManager
import io.outblock.lilico.manager.coin.OnCoinRateUpdate
import io.outblock.lilico.manager.coin.TokenStateChangeListener
import io.outblock.lilico.manager.coin.TokenStateManager
import io.outblock.lilico.manager.price.CurrencyManager
import io.outblock.lilico.manager.price.CurrencyUpdateListener
import io.outblock.lilico.manager.staking.StakingInfoUpdateListener
import io.outblock.lilico.manager.staking.StakingManager
import io.outblock.lilico.manager.transaction.TransactionStateManager
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.network.flowscan.flowScanAccountTransferCountQuery
import io.outblock.lilico.network.model.WalletListData
import io.outblock.lilico.page.profile.subpage.currency.model.selectedCurrency
import io.outblock.lilico.page.profile.subpage.wallet.ChildAccountCollectionManager
import io.outblock.lilico.page.wallet.model.WalletCoinItemModel
import io.outblock.lilico.page.wallet.model.WalletHeaderModel
import io.outblock.lilico.utils.getAccountTransferCount
import io.outblock.lilico.utils.getCurrencyFlag
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.isHideWalletBalance
import io.outblock.lilico.utils.logd
import io.outblock.lilico.utils.uiScope
import io.outblock.lilico.utils.updateAccountTransferCount
import io.outblock.lilico.utils.viewModelIOScope
import java.util.concurrent.CopyOnWriteArrayList

class WalletFragmentViewModel : ViewModel(), OnWalletDataUpdate, OnBalanceUpdate, OnCoinRateUpdate, TokenStateChangeListener, CurrencyUpdateListener, StakingInfoUpdateListener {

    val dataListLiveData = MutableLiveData<List<WalletCoinItemModel>>()

    val headerLiveData = MutableLiveData<WalletHeaderModel?>()

    private val dataList = CopyOnWriteArrayList<WalletCoinItemModel>()

    init {
        WalletFetcher.addListener(this)
        TokenStateManager.addListener(this)
        CoinRateManager.addListener(this)
        BalanceManager.addListener(this)
        CurrencyManager.addCurrencyUpdateListener(this)
        StakingManager.addStakingInfoUpdateListener(this)
    }

    fun load() {
        viewModelIOScope(this) {
            logd(TAG, "view model load")
            loadWallet()
            CurrencyManager.fetch()
        }
    }

    override fun onWalletDataUpdate(wallet: WalletListData) {
        updateWalletHeader(wallet = wallet)
        if (dataList.isEmpty()) {
            loadCoinList()
        }
        TokenStateManager.fetchState()
        StakingManager.refresh()
        ChildAccountCollectionManager.loadChildAccountTokenList()
        ioScope {
            loadTransactionCount()
        }
    }

    override fun onBalanceUpdate(coin: FlowCoin, balance: Balance) {
        logd(TAG, "onBalanceUpdate:$balance")
        updateCoinBalance(balance)
    }

    override fun onTokenStateChange() {
        loadCoinList()
        viewModelIOScope(this) { loadTransactionCount() }
    }

    override fun onCoinRateUpdate(coin: FlowCoin, price: Float, quoteChange: Float) {
        updateCoinRate(coin, price, quoteChange)
    }

    override fun onCurrencyUpdate(flag: String, price: Float) {
        ioScope {
            val currency = selectedCurrency()
            dataList.forEachIndexed { index, item -> dataList[index] = item.copy(currency = currency.flag) }
            dataListLiveData.postValue(dataList)
        }
    }

    override fun onStakingInfoUpdate() {
        val flow = dataList.firstOrNull { it.coin.isFlowCoin() } ?: return
        dataList[dataList.indexOf(flow)] = flow.copy(
            isStaked = StakingManager.isStaked(),
            stakeAmount = StakingManager.stakingCount()
        )
        dataListLiveData.value = dataList
    }

    fun onBalanceHideStateUpdate() {
        viewModelIOScope(this) {
            val isHideBalance = isHideWalletBalance()
            val data = dataList.toList().map { it.copy(isHideBalance = isHideBalance) }

            dataListLiveData.postValue(data)
        }
    }

    private suspend fun loadWallet() {
        if (WalletManager.wallet() == null) {
            headerLiveData.postValue(null)
        } else {
            updateWalletHeader(WalletManager.wallet())
        }
        WalletFetcher.fetch()
    }

    private fun loadCoinList() {
        viewModelIOScope(this) {
            val coinList = FlowCoinListManager.coinList().filter { TokenStateManager.isTokenAdded(it.address()) }

            val isHideBalance = isHideWalletBalance()
            val currency = getCurrencyFlag()
            uiScope {
                val coinToAdd = coinList.filter { coin -> dataList.none { it.coin.symbol == coin.symbol } }
                val coinToRemove = dataList.filter { coin -> coinList.none {it.symbol == coin.coin.symbol} }
                if (coinToAdd.isNotEmpty() || coinToRemove.isNotEmpty()) {
                    dataList.addAll(coinToAdd.map {
                        WalletCoinItemModel(
                            it, it.address(), 0f,
                            0f, isHideBalance = isHideBalance, currency = currency,
                            isStaked = StakingManager.isStaked(),
                            stakeAmount = StakingManager.stakingCount(),
                        )
                    })
                    dataList.removeAll(coinToRemove.toSet())
                    logd(TAG, "loadCoinList addCoin:${coinToAdd.map { it.symbol }}")
                    logd(TAG, "loadCoinList removeCoin:${coinToRemove.map { it.coin.symbol }}")
                    logd(TAG, "loadCoinList dataList:${dataList.map { it.coin.symbol }}")
                    dataListLiveData.postValue(dataList)
                    updateWalletHeader(count = coinList.size)
                }
            }

            BalanceManager.refresh()
            CoinRateManager.refresh()
        }
    }

    private suspend fun loadTransactionCount() {
        val count = flowScanAccountTransferCountQuery() + TransactionStateManager.getProcessingTransaction().size
        val localCount = getAccountTransferCount()
        if (count < localCount) {
            logd(TAG, "loadTransactionCount remote count < local count:$count < $localCount")
            return
        }
        updateAccountTransferCount(count)
        updateWalletHeader()
    }

    private fun updateCoinBalance(balance: Balance) {
        logd(TAG, "updateCoinBalance :$balance")
        val oldItem = dataList.firstOrNull { it.coin.symbol == balance.symbol } ?: return
        val item = oldItem.copy(balance = balance.balance)
        dataList[dataList.indexOf(oldItem)] = item
        dataListLiveData.value = dataList
        updateWalletHeader()
    }

    private fun updateCoinRate(coin: FlowCoin, price: Float? = null, quoteChange: Float, forceRate: Float? = null) {
        val rate = (price ?: forceRate) ?: 0f
        logd(TAG, "updateCoinRate ${coin.symbol}:$rate:$quoteChange")

        val oldItem = dataList.firstOrNull { it.coin.symbol == coin.symbol } ?: return
        val item = oldItem.copy(coinRate = rate, quoteChange = quoteChange)
        dataList[dataList.indexOf(oldItem)] = item
        dataListLiveData.value = dataList
        updateWalletHeader()
    }

    private fun updateWalletHeader(wallet: WalletListData? = null, count: Int? = null) {
        uiScope {
            val header = headerLiveData.value ?: (if (wallet == null) return@uiScope else WalletHeaderModel(wallet, 0f))
            headerLiveData.postValue(header.copy().apply {
                balance = dataList.toList().map { it.balance * it.coinRate }.sum()
                count?.let { coinCount = it }
                transactionCount = getAccountTransferCount()
            })
        }
    }

    companion object {
        private val TAG = WalletFragmentViewModel::class.java.simpleName
    }


}