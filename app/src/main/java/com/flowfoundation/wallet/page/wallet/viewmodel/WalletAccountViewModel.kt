package com.flowfoundation.wallet.page.wallet.viewmodel

import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceGetAllFlowBalance
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.main.model.LinkedAccountData
import com.flowfoundation.wallet.page.main.model.WalletAccountData
import com.flowfoundation.wallet.utils.formatLargeBalanceNumber
import com.flowfoundation.wallet.utils.ioScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.collections.forEach


class WalletAccountViewModel: ViewModel() {
    private val _accounts = MutableStateFlow<List<WalletAccountData>>(emptyList())
    val accounts: StateFlow<List<WalletAccountData>> = _accounts.asStateFlow()

    private val _balanceMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val balanceMap: StateFlow<Map<String, String>> = _balanceMap.asStateFlow()

    fun fetchWalletList(profile: Account) {
        ioScope {
            val walletList = profile.wallet?.wallets ?: return@ioScope
            val addressList = mutableListOf<String>()
            val accountList = mutableListOf<WalletAccountData>()
            walletList.forEach { wallet ->
                val address = wallet.address() ?: return@forEach
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
                accountList.add(
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
            _accounts.value = accountList
            fetchAllBalances(addressList)
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
}