package com.flowfoundation.wallet.page.account

import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.firebase.auth.firebaseUid
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.AccountVisibilityManager
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.emoji.OnEmojiUpdate
import com.flowfoundation.wallet.manager.flowjvm.cadenceGetAllFlowBalance
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.walletdata.COAWallet
import com.flowfoundation.wallet.manager.walletdata.ChildWallet
import com.flowfoundation.wallet.manager.walletdata.EOAWallet
import com.flowfoundation.wallet.manager.walletdata.FlowWallet
import com.flowfoundation.wallet.page.main.model.WalletAccountData
import com.flowfoundation.wallet.page.main.model.LinkedAccountData
import com.flowfoundation.wallet.utils.formatLargeBalanceNumber
import com.flowfoundation.wallet.utils.ioScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AccountListViewModel : ViewModel(), OnEmojiUpdate {

    private val _accounts = MutableStateFlow<List<WalletAccountData>>(emptyList())
    val accounts: StateFlow<List<WalletAccountData>> = _accounts.asStateFlow()

    private val _balanceMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val balanceMap: StateFlow<Map<String, String>> = _balanceMap.asStateFlow()

    private val _hiddenAccounts = MutableStateFlow<Set<String>>(emptySet())
    val hiddenAccounts: StateFlow<Set<String>> = _hiddenAccounts.asStateFlow()

    init {
        AccountEmojiManager.addListener(this)
    }

    fun loadData() {
        refreshWalletList()
    }

    private fun refreshWalletList(refreshBalance: Boolean = true) {
        ioScope {
            val currentNetwork = chainNetWorkString()
            val walletNodes = AccountManager.walletNodes() ?: return@ioScope

            val addressList = mutableListOf<String>()
            val accounts = mutableListOf<WalletAccountData>()

            walletNodes.forEach { mainNode ->
                when (mainNode) {
                    is EOAWallet -> {
                        val emojiInfo = AccountEmojiManager.getEmojiByAddress(mainNode.address)
                        addressList.add(mainNode.address)
                        accounts.add(
                            WalletAccountData(
                                address = mainNode.address,
                                name = emojiInfo.emojiName,
                                emojiId = emojiInfo.emojiId,
                                isSelected = WalletManager.selectedWalletAddress().equals(mainNode.address, ignoreCase = true),
                                isEOAAccount = true
                            )
                        )
                    }
                    is FlowWallet -> {
                        if (mainNode.chainIdString == currentNetwork) {
                            val emojiInfo = AccountEmojiManager.getEmojiByAddress(mainNode.address)
                            val linkedAccounts = mutableListOf<LinkedAccountData>()

                            mainNode.linkedWallets.forEach { linkedWallet ->
                                when (linkedWallet) {
                                    is ChildWallet -> {
                                        addressList.add(linkedWallet.address)
                                        linkedAccounts.add(
                                            LinkedAccountData(
                                                address = linkedWallet.address,
                                                name = linkedWallet.name,
                                                icon = linkedWallet.icon,
                                                emojiId = AccountEmojiManager.getEmojiByAddress(linkedWallet.address).emojiId,
                                                isSelected = WalletManager.selectedWalletAddress().equals(linkedWallet.address, ignoreCase = true),
                                                isCOAAccount = false
                                            )
                                        )
                                    }
                                    is COAWallet -> {
                                        val evmAddress = linkedWallet.address
                                        addressList.add(evmAddress)
                                        val linkedEmojiInfo = AccountEmojiManager.getEmojiByAddress(evmAddress)
                                        linkedAccounts.add(
                                            LinkedAccountData(
                                                address = evmAddress,
                                                name = linkedEmojiInfo.emojiName,
                                                icon = null,
                                                emojiId = linkedEmojiInfo.emojiId,
                                                isSelected = WalletManager.selectedWalletAddress().equals(evmAddress, ignoreCase = true),
                                                isCOAAccount = true
                                            )
                                        )
                                    }
                                }
                            }

                            accounts.add(
                                WalletAccountData(
                                    address = mainNode.address,
                                    name = emojiInfo.emojiName,
                                    emojiId = emojiInfo.emojiId,
                                    isSelected = WalletManager.selectedWalletAddress().equals(mainNode.address, ignoreCase = true),
                                    linkedAccounts = linkedAccounts,
                                    isEOAAccount = false
                                )
                            )
                            addressList.add(mainNode.address)
                        }
                    }
                }
            }

            // Note: AccountListViewModel shows all accounts (including hidden ones)
            // because this is the account management screen where users can see and manage all accounts
            _accounts.value = accounts

            // Update hidden accounts state
            val userId = firebaseUid()
            if (userId != null) {
                val hiddenAccountsSet = addressList.filter { address ->
                    AccountVisibilityManager.isAccountHidden(userId, address)
                }.toSet()
                _hiddenAccounts.value = hiddenAccountsSet
            }

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

    fun toggleAccountVisibility(address: String) {
        val userId = firebaseUid() ?: return
        AccountVisibilityManager.showAccount(userId, address)

        // Refresh the account list to reflect the new visibility state
        refreshWalletList(false)
    }

    override fun onEmojiUpdate(userName: String, address: String, emojiId: Int, emojiName: String) {
        refreshWalletList()
    }
}
