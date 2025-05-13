package com.flowfoundation.wallet.page.wallet

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.account.AccountInfoManager
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.OnUserInfoReload
import com.flowfoundation.wallet.manager.account.OnWalletDataUpdate
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
    OnUserInfoReload, FungibleTokenListUpdateListener, FungibleTokenUpdateListener {

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

    override fun onUserInfoReload() {
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
                FungibleTokenListManager.reload()
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
            if (list.isEmpty()) {
                return@ioScope
            }
            val isHideBalance = isHideWalletBalance()
            uiScope {
                dataList.clear()
                dataList.addAll(list.map {
                    WalletCoinItemModel(
                        it, isHideBalance, StakingManager.isStaked(), StakingManager.stakingCount()
                    )
                })
                logd(TAG, "loadCoinList dataList:${dataList.map { it.token.contractId() }}")
                sortDataList()
                dataListLiveData.postValue(dataList)
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
            ioScope {
                val isHideBalance = isHideWalletBalance()
                uiScope {
                    dataList.add(
                        WalletCoinItemModel(
                            token, isHideBalance, StakingManager.isStaked(), StakingManager.stakingCount()
                        )
                    )
                    sortDataList()
                    dataListLiveData.postValue(dataList)
                    updateWalletHeader(count = dataList.size)
                }
            }
        } else {
            val index = dataList.indexOfFirst { it.token.isSameToken(token.contractId()) }
            if (index < 0 || index >= dataList.size) {
                return
            }
            dataList.removeAt(index)
            dataListLiveData.postValue(dataList)
            updateWalletHeader(count = dataList.size)
        }
    }

    override fun onTokenUpdated(token: FungibleToken) {
        logd(TAG, "updateToken :${token.contractId()}")
        val oldItem = dataList.firstOrNull { it.token.isSameToken(token.contractId()) } ?: return
        val index = dataList.indexOf(oldItem)
        dataList[index] = oldItem.copy(token = token)
        sortDataList()
        dataListLiveData.postValue(dataList)
        updateWalletHeader()
    }

}