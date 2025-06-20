package com.flowfoundation.wallet.page.wallet

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountInfoManager
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.OnAccountUpdate
import com.flowfoundation.wallet.manager.account.OnUserInfoReload
import com.flowfoundation.wallet.manager.account.OnWalletDataUpdate
import com.flowfoundation.wallet.manager.account.WalletFetcher
import com.flowfoundation.wallet.manager.app.isMainnet
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.coin.CoinRateManager
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.coin.OnCoinRateUpdate
import com.flowfoundation.wallet.manager.price.CurrencyManager
import com.flowfoundation.wallet.manager.price.CurrencyUpdateListener
import com.flowfoundation.wallet.manager.staking.StakingInfoUpdateListener
import com.flowfoundation.wallet.manager.staking.StakingManager
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.manager.token.FungibleTokenListUpdateListener
import com.flowfoundation.wallet.manager.token.FungibleTokenUpdateListener
import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.wallet.walletAddress
import com.flowfoundation.wallet.network.model.BlockchainData
import com.flowfoundation.wallet.network.model.WalletData
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

class WalletFragmentViewModel : ViewModel(), OnAccountUpdate, OnWalletDataUpdate, OnCoinRateUpdate,
    FungibleTokenListUpdateListener, FungibleTokenUpdateListener, CurrencyUpdateListener, StakingInfoUpdateListener,
    OnUserInfoReload {

    val dataListLiveData = MutableLiveData<List<WalletCoinItemModel>>()
    val headerLiveData = MutableLiveData<WalletHeaderModel?>()
    private val dataList = CopyOnWriteArrayList<WalletCoinItemModel>()
    private var needReload = true

    init {
        AccountManager.addListener(this)
        WalletFetcher.addListener(this)
        FungibleTokenListManager.addTokenListUpdateListener(this)
        FungibleTokenListManager.addTokenUpdateListener(this)
        CoinRateManager.addListener(this)
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

    override fun onTokenUpdated(token: FungibleToken) {
        logd(TAG, "onTokenUpdated:$token")
        updateTokenBalance(token)
    }

    override fun onTokenListUpdated(list: List<FungibleToken>) {
        logd(TAG, "onTokenListUpdated(): ${list.size}")
        loadTokenList(list)
    }

    override fun onTokenDisplayUpdated(token: FungibleToken, isAdd: Boolean) {
        if (isAdd) {
            addTokenToList(token)
        } else {
            removeTokenFromList(token)
        }
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
            val wallet = WalletManager.wallet()
            val walletData = WalletListData(
                id = "",
                username = "",
                wallets = listOf(WalletData(
                    blockchain = listOf(BlockchainData(
                        address = wallet?.walletAddress() ?: "",
                        chainId = chainNetWorkString()
                    )),
                    name = ""
                ))
            )
            updateWalletHeader(wallet = walletData)
            needReload = true
            loadCoinInfo(isRefresh)
        }
        WalletFetcher.fetch()
    }

    private fun loadCoinInfo(isRefresh: Boolean) {
        if (needReload) {
            needReload = false
            AccountInfoManager.refreshAccountInfo()
            if (isRefresh || dataList.isEmpty()) {
                // Wait for wallet to be properly initialized before fetching token state
                viewModelIOScope(this) {
                    var retryCount = 0
                    while (retryCount < 10) { // Max 10 retries (5 seconds)
                        val walletAddress = WalletManager.selectedWalletAddress()
                        if (walletAddress.isNotBlank() && walletAddress.length > 10) {
                            FungibleTokenListManager.reload()
                            break
                        } else {
                            kotlinx.coroutines.delay(500)
                            retryCount++
                        }
                    }
                    
                    if (retryCount >= 10) {
                        logd(TAG, "loadCoinInfo :: timeout waiting for wallet address, skipping token state fetch")
                    }
                }
            }
            ChildAccountCollectionManager.loadChildAccountTokenList()
        }
    }

    private fun loadTokenList(tokens: List<FungibleToken>) {
        viewModelIOScope(this) {
            logd(TAG, "tokenList :: ${tokens.size}")
            if (tokens.isEmpty()) {
                return@viewModelIOScope
            }

            val isHideBalance = isHideWalletBalance()
            val currency = getCurrencyFlag()
            uiScope {
                // Convert FungibleTokens to FlowCoins for backwards compatibility with existing UI
                val coinList = tokens.mapNotNull { token ->
                    FlowCoinListManager.coinList().firstOrNull { coin ->
                        coin.contractId() == token.contractId()
                    }
                }

                val coinToAdd =
                    coinList.filter { coin -> dataList.none { it.coin.isSameCoin(coin.contractId()) } }
                val coinToRemove =
                    dataList.filter { coin -> coinList.none { it.isSameCoin(coin.coin.contractId()) } }
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
                    val filteredList = dataList.distinctBy { it.coin.contractId() }
                    dataList.clear()
                    dataList.addAll(filteredList)
                    dataListLiveData.postValue(filteredList)
                    updateWalletHeader(count = filteredList.size)
                }
            }

            CoinRateManager.refresh()
            if (isMainnet() && WalletManager.isEVMAccountSelected().not() && WalletManager.isChildAccountSelected().not()) {
                StakingManager.refresh()
            }
        }
    }

    private fun updateTokenBalance(token: FungibleToken) {
        val oldItem = dataList.firstOrNull {
            it.coin.contractId() == token.contractId()
        }
        
        if (oldItem == null) {
            return
        }
        
        val item = oldItem.copy(balance = token.tokenBalance())
        dataList[dataList.indexOf(oldItem)] = item
        sortDataList()
        dataListLiveData.value = dataList
        updateWalletHeader()
    }

    private fun addTokenToList(token: FungibleToken) {
        val coin = FlowCoinListManager.coinList().firstOrNull { it.contractId() == token.contractId() } ?: return
        val isHideBalance = isHideWalletBalance()
        val currency = getCurrencyFlag()
        
        val newItem = WalletCoinItemModel(
            coin, coin.address, token.tokenBalance(),
            BigDecimal.ZERO, isHideBalance = isHideBalance, currency = currency,
            isStaked = StakingManager.isStaked(),
            stakeAmount = StakingManager.stakingCount(),
        )
        
        dataList.add(newItem)
        sortDataList()
        dataListLiveData.value = dataList
        updateWalletHeader(count = dataList.size)
    }

    private fun removeTokenFromList(token: FungibleToken) {
        val removed = dataList.removeAll { it.coin.contractId() == token.contractId() }
        if (removed) {
            dataListLiveData.value = dataList
            updateWalletHeader(count = dataList.size)
        }
    }

    private fun updateCoinRate(
        coin: FlowCoin,
        rate: BigDecimal? = null,
        quoteChange: Float
    ) {
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

    override fun onAccountUpdate(account: Account) {
        viewModelIOScope(this) {
            loadWallet(true)
        }
    }

    companion object {
        private val TAG = WalletFragmentViewModel::class.java.simpleName
    }

}