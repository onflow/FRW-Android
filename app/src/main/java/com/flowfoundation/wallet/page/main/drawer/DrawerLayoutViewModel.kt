package com.flowfoundation.wallet.page.main.drawer

import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.OnWalletDataUpdate
import com.flowfoundation.wallet.manager.account.WalletFetcher
import com.flowfoundation.wallet.manager.childaccount.ChildAccount
import com.flowfoundation.wallet.manager.childaccount.ChildAccountList
import com.flowfoundation.wallet.manager.childaccount.ChildAccountUpdateListenerCallback
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.emoji.OnEmojiUpdate
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceGetAllFlowBalance
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.model.WalletListData
import com.flowfoundation.wallet.utils.formatLargeBalanceNumber
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.page.main.model.LinkedAccountData
import com.flowfoundation.wallet.page.main.model.WalletAccountData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DrawerLayoutViewModel : ViewModel(), ChildAccountUpdateListenerCallback, OnWalletDataUpdate, OnEmojiUpdate {

    private val _userInfo = MutableStateFlow<UserInfoData?>(null)
    val userInfo: StateFlow<UserInfoData?> = _userInfo.asStateFlow()

    private val _showEvmLayout = MutableStateFlow(false)
    val showEvmLayout: StateFlow<Boolean> = _showEvmLayout.asStateFlow()

    private val _accounts = MutableStateFlow<List<WalletAccountData>>(emptyList())
    val accounts: StateFlow<List<WalletAccountData>> = _accounts.asStateFlow()

    private val _balanceMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val balanceMap: StateFlow<Map<String, String>> = _balanceMap.asStateFlow()

    init {
        ChildAccountList.addAccountUpdateListener(this)
        WalletFetcher.addListener(this)
        AccountEmojiManager.addListener(this)
    }

    fun loadData() {
        loadEvmStatus()
        refreshWalletList()
    }

    private fun loadEvmStatus() {
        _showEvmLayout.value = EVMWalletManager.showEVMEnablePage()
    }

    fun refreshWalletList(refreshBalance: Boolean = false) {
        ioScope {
            _userInfo.value = AccountManager.userInfo() ?: return@ioScope
            val wallet = WalletManager.wallet()?.wallet() ?: return@ioScope
            val list = mutableListOf(wallet)
            val addressList = mutableListOf<String>()
            val accounts = mutableListOf<WalletAccountData>()
            list.forEach { walletItem ->
                val address = walletItem.address() ?: return@forEach
                val emojiInfo = AccountEmojiManager.getEmojiByAddress(address)
                val linkedAccounts = mutableListOf<LinkedAccountData>()
                WalletManager.childAccountList(address)?.get()?.forEach { childAccount ->
                    addressList.add(childAccount.address)
                    linkedAccounts.add(
                        LinkedAccountData(
                            address = childAccount.address,
                            name = childAccount.name,
                            emojiId = AccountEmojiManager.getEmojiByAddress(childAccount.address).emojiId,
                            isSelected = WalletManager.selectedWalletAddress() == childAccount.address,
                            isEVMAccount = false
                        )
                    )
                }
                EVMWalletManager.getEVMAddress()?.let {
                    addressList.add(it)
                    val emojiInfo = AccountEmojiManager.getEmojiByAddress(it)
                    linkedAccounts.add(
                        LinkedAccountData(
                            address = it,
                            name = emojiInfo.emojiName,
                            emojiId = emojiInfo.emojiId,
                            isSelected = WalletManager.selectedWalletAddress() == it,
                            isEVMAccount = true
                        )
                    )
                }
                accounts.add(
                    WalletAccountData(
                        address = address,
                        name = emojiInfo.emojiName,
                        emojiId = emojiInfo.emojiId,
                        isSelected = WalletManager.selectedWalletAddress() == address,
                        linkedAccounts = linkedAccounts
                    )
                )
                addressList.add(address)
            }
            _accounts.value = accounts
            if (refreshBalance) {
                fetchAllBalances(addressList)
            }
        }
    }

    private fun fetchAllBalances(addressList: List<String>) {
        ioScope {
            val balanceMap = cadenceGetAllFlowBalance(addressList) ?: return@ioScope
            val formattedBalanceMap = balanceMap.mapValues { (_, balance) ->
                "${balance.formatLargeBalanceNumber(isAbbreviation = true)} FLOW"
            }
            _balanceMap.value = formattedBalanceMap
        }
    }

    override fun onChildAccountUpdate(parentAddress: String, accounts: List<ChildAccount>) {
        refreshWalletList(true)
    }

    override fun onWalletDataUpdate(wallet: WalletListData) {
        refreshWalletList(true)
    }

    override fun onEmojiUpdate(userName: String, address: String, emojiId: Int, emojiName: String) {
        refreshWalletList()
    }
}