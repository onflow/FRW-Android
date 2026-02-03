package com.flowfoundation.wallet.page.wallet

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountInfoManager
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.OnUserInfoReload
import com.flowfoundation.wallet.manager.account.OnAccountUpdate
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
import com.flowfoundation.wallet.manager.walletdata.WalletDataManager
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WalletFragmentViewModel : ViewModel(), CurrencyUpdateListener, StakingInfoUpdateListener,
    OnUserInfoReload, FungibleTokenListUpdateListener, FungibleTokenUpdateListener, OnAccountUpdate {

    val dataListLiveData = MutableLiveData<List<WalletCoinItemModel>>()

    val headerLiveData = MutableLiveData<WalletHeaderModel?>()

    private val dataList = CopyOnWriteArrayList<WalletCoinItemModel>()

    // Add mutex lock to ensure thread safety
    private val updateLock = Mutex()

    // Cache latest staking information
    private var cachedStakingInfo: Pair<Boolean, Float>? = null

    private var needReload = true

    init {
        AccountManager.addListener(this)
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
            if (isRefresh) {
                WalletDataManager.updateCurrentAccount()
            }
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
                filteredTokens = filteredTokens.filter { it.tokenBalanceInUSD() > BigDecimal(0.01) }
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
                // Use centralized update method to preserve staking info
                updateDataListSafely(finalTokens, preserveStakingInfo = true)
                logd(TAG, "refreshWithCurrentTokens: Updated UI with ${finalTokens.size} tokens")
            }
        }
    }

    override fun onUserInfoReload() {
        viewModelIOScope(this) {
            loadWallet(true)
        }
    }

    override fun onAccountUpdate(account: Account) {
        viewModelIOScope(this) {
            loadWallet(true)
        }
    }

    override fun onCurrencyUpdate(flag: String, price: Float) {
        ioScope {
            FungibleTokenListManager.updateTokenList()
        }
    }

    /**
     * Centralized data list update method to ensure staking info is not lost
     */
    private suspend fun updateDataListSafely(
        tokens: List<FungibleToken>,
        preserveStakingInfo: Boolean = true
    ) {
        updateLock.withLock {
            val isHideBalance = isHideWalletBalance()

            // If need to preserve staking info, get current staking state first
            val currentStakingInfo = if (preserveStakingInfo) {
                cachedStakingInfo ?: Pair(StakingManager.isStaked(), StakingManager.stakingCount())
            } else {
                Pair(StakingManager.isStaked(), StakingManager.stakingCount())
            }

            val newDataList = tokens.map { token ->
                WalletCoinItemModel(
                    token = token,
                    isHideBalance = isHideBalance,
                    isStaked = if (token.isFlowToken()) currentStakingInfo.first else false,
                    stakeAmount = if (token.isFlowToken()) currentStakingInfo.second else 0f
                )
            }

            uiScope {
                dataList.clear()
                dataList.addAll(newDataList)
                sortDataList()
                dataListLiveData.postValue(dataList.toList())
                updateWalletHeader(count = dataList.size)

                logd(TAG, "updateDataListSafely: Updated ${newDataList.size} tokens, Flow staking: isStaked=${currentStakingInfo.first}, amount=${currentStakingInfo.second}")
            }
        }
    }

    override fun onStakingInfoUpdate() {
        logd(TAG, "onStakingInfoUpdate called")
        viewModelIOScope(this) {
            val isStaked = StakingManager.isStaked()
            val stakingCount = StakingManager.stakingCount()

            // Cache latest staking information
            cachedStakingInfo = Pair(isStaked, stakingCount)

            logd(TAG, "onStakingInfoUpdate: isStaked=$isStaked, stakingCount=$stakingCount")

            updateLock.withLock {
                val updatedList = dataList.map { item ->
                    if (item.token.isFlowToken()) {
                        item.copy(isStaked = isStaked, stakeAmount = stakingCount)
                    } else {
                        item
                    }
                }

                uiScope {
                    dataList.clear()
                    dataList.addAll(updatedList)
                    dataListLiveData.postValue(dataList.toList())
                    logd(TAG, "onStakingInfoUpdate: Successfully updated Flow token staking info")
                }
            }
        }
    }

    fun onBalanceHideStateUpdate() {
        viewModelIOScope(this) {
            updateLock.withLock {
                val isHideBalance = isHideWalletBalance()
                val updatedList = dataList.map { it.copy(isHideBalance = isHideBalance) }

                uiScope {
                    dataList.clear()
                    dataList.addAll(updatedList)
                    dataListLiveData.postValue(dataList.toList())
                    logd(TAG, "onBalanceHideStateUpdate: Updated hide balance state to $isHideBalance")
                }
            }
        }
    }

    private fun loadWallet(isRefresh: Boolean) {
        val wallet = AccountManager.get()?.wallet
        if (wallet == null) {
            headerLiveData.postValue(null)
            dataList.clear()
            dataListLiveData.postValue(emptyList())
            needReload = true
            logd(TAG, "loadWallet :: null")
        } else {
            logd(TAG, "loadWallet :: wallet")
            updateWalletHeader(wallet)
            needReload = true
            loadCoinInfo(isRefresh)
        }
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
                    if (displayTokens.isNotEmpty()) {
                        updateDataListSafely(displayTokens, preserveStakingInfo = true)
                    } else {
                        updateLock.withLock {
                            uiScope {
                                dataList.clear()
                                dataListLiveData.postValue(emptyList())
                                updateWalletHeader(count = 0)
                                logd(TAG, "loadCoinInfo: No tokens to display (filtered out)")
                            }
                        }
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
        viewModelIOScope(this) {
            logd(TAG, "onTokenListUpdated: ${list.size} tokens")
            val displayTokens = FungibleTokenListManager.getCurrentDisplayTokenListSnapshot()
            logd(TAG, "onTokenListUpdated: displayTokens.size=${displayTokens.size}")

            // Use centralized update method to preserve staking info
            if (displayTokens.isNotEmpty()) {
                updateDataListSafely(displayTokens, preserveStakingInfo = true)
                logd(TAG, "onTokenListUpdated: Updated UI with displayTokens")
            } else {
                updateLock.withLock {
                    uiScope {
                        dataList.clear()
                        dataListLiveData.postValue(emptyList())
                        updateWalletHeader(count = 0)
                        logd(TAG, "onTokenListUpdated: No tokens to display (filtered out or empty)")
                    }
                }
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
            viewModelIOScope(this) {
                updateLock.withLock {
                    val isHideBalance = isHideWalletBalance()
                    val stakingInfo = cachedStakingInfo ?: Pair(StakingManager.isStaked(), StakingManager.stakingCount())

                    val newItem = WalletCoinItemModel(
                        token = token,
                        isHideBalance = isHideBalance,
                        isStaked = if (token.isFlowToken()) stakingInfo.first else false,
                        stakeAmount = if (token.isFlowToken()) stakingInfo.second else 0f
                    )

                    uiScope {
                        dataList.add(newItem)
                        sortDataList()
                        dataListLiveData.postValue(dataList.toList())
                        updateWalletHeader(count = dataList.size)
                        logd(TAG, "onTokenDisplayUpdated: Added token ${token.contractId()}")
                    }
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
        logd(TAG, "onTokenUpdated: ${token.contractId()}")
        viewModelIOScope(this) {
            updateLock.withLock {
                val index = dataList.indexOfFirst { it.token.isSameToken(token.contractId()) }
                if (index >= 0) {
                    val oldItem = dataList[index]
                    // Preserve existing staking information
                    val newItem = oldItem.copy(token = token)

                    uiScope {
                        dataList[index] = newItem
                        sortDataList()
                        dataListLiveData.postValue(dataList.toList())
                        updateWalletHeader()
                        logd(TAG, "onTokenUpdated: Successfully updated token ${token.contractId()}")
                    }
                } else {
                    logd(TAG, "onTokenUpdated: Token ${token.contractId()} not found in dataList")
                }
            }
        }
    }

}
