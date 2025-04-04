package com.flowfoundation.wallet.page.wallet

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.account.AccountInfoManager
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.Balance
import com.flowfoundation.wallet.manager.account.BalanceManager
import com.flowfoundation.wallet.manager.account.OnBalanceUpdate
import com.flowfoundation.wallet.manager.account.OnUserInfoReload
import com.flowfoundation.wallet.manager.account.OnWalletDataUpdate
import com.flowfoundation.wallet.manager.account.WalletFetcher
import com.flowfoundation.wallet.manager.app.isMainnet
import com.flowfoundation.wallet.manager.coin.CoinRateManager
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.coin.OnCoinRateUpdate
import com.flowfoundation.wallet.manager.coin.TokenStateChangeListener
import com.flowfoundation.wallet.manager.coin.TokenStateManager
import com.flowfoundation.wallet.manager.price.CurrencyManager
import com.flowfoundation.wallet.manager.price.CurrencyUpdateListener
import com.flowfoundation.wallet.manager.staking.StakingInfoUpdateListener
import com.flowfoundation.wallet.manager.staking.StakingManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.model.WalletListData
import com.flowfoundation.wallet.page.profile.subpage.currency.model.selectedCurrency
import com.flowfoundation.wallet.page.profile.subpage.wallet.ChildAccountCollectionManager
import com.flowfoundation.wallet.page.wallet.model.WalletCoinItemModel
import com.flowfoundation.wallet.page.wallet.model.WalletHeaderModel
import com.flowfoundation.wallet.utils.getCurrencyFlag
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.isHideWalletBalance
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.utils.viewModelIOScope
import java.math.BigDecimal
import java.util.concurrent.CopyOnWriteArrayList

class WalletFragmentViewModel : ViewModel(), OnWalletDataUpdate, OnBalanceUpdate, OnCoinRateUpdate,
    TokenStateChangeListener, CurrencyUpdateListener, StakingInfoUpdateListener,
    OnUserInfoReload {

    val dataListLiveData = MutableLiveData<List<WalletCoinItemModel>>()

    val headerLiveData = MutableLiveData<WalletHeaderModel?>()

    private val dataList = CopyOnWriteArrayList<WalletCoinItemModel>()

    private var needReload = true

    init {
        AccountManager.addListener(this)
        WalletFetcher.addListener(this)
        TokenStateManager.addListener(this)
        CoinRateManager.addListener(this)
        BalanceManager.addListener(this)
        CurrencyManager.addCurrencyUpdateListener(this)
        StakingManager.addStakingInfoUpdateListener(this)
    }

    fun load(isRefresh: Boolean = false) {
        viewModelIOScope(this) {
            logd(TAG, "view model load")
            loadWallet(isRefresh)
            CurrencyManager.fetch()
        }
    }

    override fun onUserInfoReload() {
        viewModelIOScope(this) {
            loadWallet(true)
        }
    }

    override fun onWalletDataUpdate(wallet: WalletListData) {
        updateWalletHeader(wallet = wallet)
        loadCoinInfo(false)
    }

    override fun onBalanceUpdate(coin: FlowCoin, balance: Balance) {
        logd(TAG, "onBalanceUpdate:$balance")
        updateCoinBalance(balance)
    }

    override fun onTokenStateChange() {
        logd(TAG, "onTokenStateChange()")
        loadCoinList()
    }

    override fun onCoinRateUpdate(coin: FlowCoin, price: BigDecimal, quoteChange: Float) {
        updateCoinRate(coin, price, quoteChange)
    }

    override fun onCurrencyUpdate(flag: String, price: Float) {
        ioScope {
            val currency = selectedCurrency()
            dataList.forEachIndexed { index, item ->
                dataList[index] = item.copy(currency = currency.flag)
            }
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

    private fun loadWallet(isRefresh: Boolean) {
        if (WalletManager.wallet() == null) {
            headerLiveData.postValue(null)
            dataList.clear()
            dataListLiveData.postValue(emptyList())
            needReload = true
            logd(TAG, "loadWallet :: null")
        } else {
            logd(TAG, "loadWallet :: wallet")
            updateWalletHeader(WalletManager.wallet())
            needReload = true
            loadCoinInfo(isRefresh)
        }
        WalletFetcher.fetch()
    }

    private fun loadCoinInfo(isRefresh: Boolean) {
        if (needReload) {
            needReload = false
            AccountInfoManager.refreshAccountInfo()
            logd(TAG, "loadCoinInfo :: isRefresh :: $isRefresh")
            logd(TAG, "loadCoinInfo :: dataList :: ${dataList.size}")
            if (isRefresh || dataList.isEmpty()) {
                logd(TAG, "loadCoinInfo :: fetchState")
                TokenStateManager.fetchState()
            }
            ChildAccountCollectionManager.loadChildAccountTokenList()
        }
    }

    private fun loadCoinList() {
        viewModelIOScope(this) {
            val coinList =
                FlowCoinListManager.coinList().filter { TokenStateManager.isTokenAdded(it) }
            logd(TAG, "coinList :: ${coinList.size}")
            if (coinList.isEmpty()) {
                return@viewModelIOScope
            }

            val isHideBalance = isHideWalletBalance()
            val currency = getCurrencyFlag()
            uiScope {
                val coinToAdd =
                    coinList.filter { coin -> dataList.none { it.coin.isSameCoin(coin.contractId()) } }
                val coinToRemove =
                    dataList.filter { coin -> coinList.none { it.isSameCoin(coin.coin.contractId()) } }
                logd(TAG, "loadCoinList coinToAdd::${coinToAdd.map { it.contractId() }}")
                logd(TAG, "loadCoinList coinToRemove::${coinToRemove.map { it.coin.contractId() }}")
                if (coinToAdd.isNotEmpty() || coinToRemove.isNotEmpty()) {
                    dataList.addAll(coinToAdd.map {
                        WalletCoinItemModel(
                            it, it.address, BigDecimal.ZERO,
                            BigDecimal.ZERO, isHideBalance = isHideBalance, currency = currency,
                            isStaked = StakingManager.isStaked(),
                            stakeAmount = StakingManager.stakingCount(),
                        )
                    })
                    dataList.removeAll(coinToRemove.toSet())
                    logd(TAG, "loadCoinList addCoin:${coinToAdd.map { it.contractId() }}")
                    logd(TAG, "loadCoinList removeCoin:${coinToRemove.map { it.coin.contractId() }}")
                    logd(TAG, "loadCoinList dataList:${dataList.map { it.coin.contractId() }}")
                    val filteredList = dataList.distinctBy { it.coin.contractId() }
                    dataList.clear()
                    dataList.addAll(filteredList)
                    dataListLiveData.postValue(filteredList)
                    updateWalletHeader(count = filteredList.size)
                }
            }

            BalanceManager.refresh()
            CoinRateManager.refresh()
            if (isMainnet() && WalletManager.isEVMAccountSelected().not() && WalletManager.isChildAccountSelected().not()) {
                StakingManager.refresh()
            }
        }
    }

    private fun updateCoinBalance(balance: Balance) {
        logd(TAG, "updateCoinBalance :$balance")
        val oldItem = dataList.firstOrNull { balance.isSameCoin(it.coin) } ?: return
        val item = oldItem.copy(balance = balance.balance)
        dataList[dataList.indexOf(oldItem)] = item
        sortDataList()
        dataListLiveData.value = dataList
        updateWalletHeader()
    }

    private fun updateCoinRate(
        coin: FlowCoin,
        rate: BigDecimal? = null,
        quoteChange: Float
    ) {
        logd(TAG, "updateCoinRate ${coin.contractId()}:$rate:$quoteChange")

        val oldItem = dataList.firstOrNull { it.coin.isSameCoin(coin.contractId()) } ?: return
        val item = oldItem.copy(coinRate = rate ?: BigDecimal.ZERO, quoteChange = quoteChange)
        dataList[dataList.indexOf(oldItem)] = item
        sortDataList()
        dataListLiveData.value = dataList
        updateWalletHeader()
    }

    private fun sortDataList() {
        val mutableData = dataList.toMutableList()
        val comparator = compareByDescending<WalletCoinItemModel> { it.balance * it.coinRate }
            .thenByDescending { it.balance }
        mutableData.sortWith(comparator)
        dataList.clear()
        dataList.addAll(mutableData)
    }

    private fun updateWalletHeader(wallet: WalletListData? = null, count: Int? = null) {
        uiScope {
            val header =
                headerLiveData.value ?: (if (wallet == null) return@uiScope else WalletHeaderModel(
                    wallet,
                    BigDecimal.ZERO
                ))
            headerLiveData.postValue(header.copy().apply {
                balance = dataList.toList().map { it.balance * it.coinRate }.fold(BigDecimal.ZERO) { sum, value -> sum + value }
                count?.let { coinCount = it }
            })
        }
    }

    companion object {
        private val TAG = WalletFragmentViewModel::class.java.simpleName
    }

}