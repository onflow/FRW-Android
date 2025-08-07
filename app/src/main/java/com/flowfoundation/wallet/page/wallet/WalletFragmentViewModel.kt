package com.flowfoundation.wallet.page.wallet

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.account.AccountInfoManager
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.OnUserInfoReload
import com.flowfoundation.wallet.manager.account.OnWalletDataUpdate
import com.flowfoundation.wallet.manager.account.OnAccountUpdate
import com.flowfoundation.wallet.manager.account.WalletFetcher
import com.flowfoundation.wallet.manager.app.isMainnet
import com.flowfoundation.wallet.manager.price.CurrencyManager
import com.flowfoundation.wallet.manager.price.CurrencyUpdateListener
import com.flowfoundation.wallet.manager.staking.StakingInfoUpdateListener
import com.flowfoundation.wallet.manager.staking.StakingManager
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.manager.token.FungibleTokenListUpdateListener
import com.flowfoundation.wallet.manager.token.FungibleTokenUpdateListener
import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.model.WalletListData
import com.flowfoundation.wallet.page.profile.subpage.wallet.ChildAccountCollectionManager
import com.flowfoundation.wallet.page.wallet.model.WalletCoinItemModel
import com.flowfoundation.wallet.page.wallet.model.WalletHeaderModel
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.isHideWalletBalance
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.utils.viewModelIOScope
import java.math.BigDecimal
import java.util.concurrent.CopyOnWriteArrayList

class WalletFragmentViewModel : ViewModel(), OnWalletDataUpdate, CurrencyUpdateListener, StakingInfoUpdateListener,
    OnUserInfoReload, FungibleTokenListUpdateListener, FungibleTokenUpdateListener, OnAccountUpdate {

    val dataListLiveData = MutableLiveData<List<WalletCoinItemModel>>()

    val headerLiveData = MutableLiveData<WalletHeaderModel?>()

    private val dataList = CopyOnWriteArrayList<WalletCoinItemModel>()

    private var needReload = true

    init {
        AccountManager.addListener(this)
        WalletFetcher.addListener(this)
        FungibleTokenListManager.addTokenUpdateListener(this)
        FungibleTokenListManager.addTokenListUpdateListener(this)
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

    fun refreshWithCurrentTokens() {
        viewModelIOScope(this) {
            // Force re-sync with properly filtered tokens
            // Get all tokens first, then apply current filters manually to ensure consistency
            val allTokens = FungibleTokenListManager.getCurrentTokenListSnapshot()
            val isHideDust = FungibleTokenListManager.isHideDustTokens()
            val isOnlyVerified = FungibleTokenListManager.isOnlyShowVerifiedTokens()
            
            logd(TAG, "refreshWithCurrentTokens: isHideDustTokens=$isHideDust, isOnlyVerified=$isOnlyVerified")
            logd(TAG, "refreshWithCurrentTokens: allTokens.size=${allTokens.size}")
            
            // Apply filters manually to ensure consistency
            var filteredTokens = allTokens
            
            if (isHideDust) {
                filteredTokens = filteredTokens.filter { it.tokenBalanceInUSD() > java.math.BigDecimal(0.01) }
                logd(TAG, "refreshWithCurrentTokens: After dust filter: ${filteredTokens.size}")
            }
            
            if (isOnlyVerified) {
                filteredTokens = filteredTokens.filter { it.isVerified }
                logd(TAG, "refreshWithCurrentTokens: After verified filter: ${filteredTokens.size}")
            }
            
            // Only show tokens that are in the current display list (user selected)
            val displayTokens = FungibleTokenListManager.getCurrentDisplayTokenListSnapshot()
            val finalTokens = filteredTokens.filter { token ->
                displayTokens.any { it.isSameToken(token.contractId()) }
            }
            
            logd(TAG, "refreshWithCurrentTokens: Final tokens: ${finalTokens.size}")
            finalTokens.forEach { token ->
                logd(TAG, "refreshWithCurrentTokens: ${token.symbol} balance=${token.tokenBalanceInUSD()}")
            }
            
            if (finalTokens.isNotEmpty() || allTokens.isNotEmpty()) {
                val isHideBalance = isHideWalletBalance()
                uiScope {
                    dataList.clear()
                    dataList.addAll(finalTokens.map {
                        WalletCoinItemModel(
                            it, isHideBalance, StakingManager.isStaked(), StakingManager.stakingCount()
                        )
                    })
                    sortDataList()
                    dataListLiveData.postValue(dataList.toList())
                    updateWalletHeader(count = dataList.size)
                    logd(TAG, "refreshWithCurrentTokens: Updated UI with ${finalTokens.size} tokens")
                }
            }
        }
    }

    override fun onUserInfoReload() {
        viewModelIOScope(this) {
            loadWallet(true)
        }
    }

    override fun onAccountUpdate(account: com.flowfoundation.wallet.manager.account.Account) {
        viewModelIOScope(this) {
            loadWallet(true)
        }
    }

    override fun onWalletDataUpdate(wallet: WalletListData) {
        updateWalletHeader(wallet = wallet)
        loadCoinInfo(false)
    }

    override fun onCurrencyUpdate(flag: String, price: Float) {
        ioScope {
            FungibleTokenListManager.updateTokenList()
        }
    }

    override fun onStakingInfoUpdate() {
        val flow = dataList.firstOrNull { it.token.isFlowToken() } ?: return
        dataList[dataList.indexOf(flow)] = flow.copy(
            isStaked = StakingManager.isStaked(),
            stakeAmount = StakingManager.stakingCount()
        )
        dataListLiveData.postValue(dataList.toList())
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
            updateWalletHeader()
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
                FungibleTokenListManager.reload()
            } else {
                // If not reloading, make sure we still use the current filtered display tokens
                val displayTokens = FungibleTokenListManager.getCurrentDisplayTokenListSnapshot()
                logd(TAG, "loadCoinInfo: displayTokens.size=${displayTokens.size}")
                ioScope {
                    val isHideBalance = isHideWalletBalance()
                    uiScope {
                        dataList.clear()
                        if (displayTokens.isNotEmpty()) {
                            dataList.addAll(displayTokens.map {
                                WalletCoinItemModel(
                                    it, isHideBalance, StakingManager.isStaked(), StakingManager.stakingCount()
                                )
                            })
                        } else {
                            logd(TAG, "loadCoinInfo: No tokens to display (filtered out)")
                        }
                        sortDataList()
                        dataListLiveData.postValue(dataList.toList())
                        updateWalletHeader(count = dataList.size)
                    }
                }
            }
            ChildAccountCollectionManager.loadChildAccountTokenList()
        }
    }

    private fun sortDataList() {
        val mutableData = dataList.toMutableList()
        val comparator = compareByDescending<WalletCoinItemModel> { it.token.tokenBalancePrice() }
            .thenByDescending { it.token.tokenBalance() }
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
                balance = dataList.toList().map { it.token.tokenBalancePrice() }.fold(BigDecimal.ZERO) { sum, value -> sum + value }
                count?.let { coinCount = it }
            })
        }
    }

    companion object {
        private val TAG = WalletFragmentViewModel::class.java.simpleName
    }

    override fun onTokenListUpdated(list: List<FungibleToken>) {
        ioScope {
            logd(TAG, "coinList :: ${list.size}")
            val displayTokens = FungibleTokenListManager.getCurrentDisplayTokenListSnapshot()
            logd(TAG, "onTokenListUpdated: displayTokens.size=${displayTokens.size}")
            
            val isHideBalance = isHideWalletBalance()
            uiScope {
                dataList.clear()
                if (displayTokens.isNotEmpty()) {
                    dataList.addAll(displayTokens.map {
                        WalletCoinItemModel(
                            it, isHideBalance, StakingManager.isStaked(), StakingManager.stakingCount()
                        )
                    })
                    logd(TAG, "loadCoinList dataList:${dataList.map { it.token.contractId() }}")
                } else {
                    logd(TAG, "onTokenListUpdated: No tokens to display (filtered out or empty)")
                }
                sortDataList()
                dataListLiveData.postValue(dataList.toList())
                updateWalletHeader(count = dataList.size)
            }
            if (isMainnet() && WalletManager.isEVMAccountSelected().not() && WalletManager.isChildAccountSelected().not()) {
                StakingManager.refresh()
            }
        }
    }

    override fun onTokenDisplayUpdated(token: FungibleToken, isAdd: Boolean) {
        if (isAdd) {
            if (dataList.any { it.token.isSameToken(token.contractId()) }) {
                return
            }
            // Check if token should be displayed based on current filters
            val displayTokens = FungibleTokenListManager.getCurrentDisplayTokenListSnapshot()
            if (displayTokens.none { it.isSameToken(token.contractId()) }) {
                return // Token is filtered out, don't add it
            }
            ioScope {
                val isHideBalance = isHideWalletBalance()
                uiScope {
                    dataList.add(
                        WalletCoinItemModel(
                            token, isHideBalance, StakingManager.isStaked(), StakingManager.stakingCount()
                        )
                    )
                    sortDataList()
                    dataListLiveData.postValue(dataList.toList())
                    updateWalletHeader(count = dataList.size)
                }
            }
        } else {
            val index = dataList.indexOfFirst { it.token.isSameToken(token.contractId()) }
            if (index < 0 || index >= dataList.size) {
                return
            }
            dataList.removeAt(index)
            dataListLiveData.postValue(dataList.toList())
            updateWalletHeader(count = dataList.size)
        }
    }

    override fun onTokenUpdated(token: FungibleToken) {
        logd(TAG, "updateToken :${token.contractId()}")
        val oldItem = dataList.firstOrNull { it.token.isSameToken(token.contractId()) } ?: return
        val index = dataList.indexOf(oldItem)
        dataList[index] = oldItem.copy(token = token)
        sortDataList()
        dataListLiveData.postValue(dataList.toList())
        updateWalletHeader()
    }

}