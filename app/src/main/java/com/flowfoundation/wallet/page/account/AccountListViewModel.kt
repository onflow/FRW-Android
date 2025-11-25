package com.flowfoundation.wallet.page.account

import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.firebase.auth.firebaseUid
import com.flowfoundation.wallet.manager.account.AccountVisibilityManager
import com.flowfoundation.wallet.manager.app.NETWORK_NAME_MAINNET
import com.flowfoundation.wallet.manager.app.NETWORK_NAME_TESTNET
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.emoji.OnEmojiUpdate
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceGetAllFlowBalance
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.retrofitApi
import java.math.BigDecimal
import com.flowfoundation.wallet.page.main.model.WalletAccountData
import com.flowfoundation.wallet.page.main.model.LinkedAccountData
import com.flowfoundation.wallet.utils.formatLargeBalanceNumber
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.wallet.toAddress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.onflow.flow.ChainId

class AccountListViewModel : ViewModel(), OnEmojiUpdate {

    private val _accounts = MutableStateFlow<List<WalletAccountData>>(emptyList())
    val accounts: StateFlow<List<WalletAccountData>> = _accounts.asStateFlow()

    private val _balanceMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val balanceMap: StateFlow<Map<String, String>> = _balanceMap.asStateFlow()

    private val service by lazy { retrofitApi().create(ApiService::class.java) }

    // Cache for verified EVM addresses that should be included in linkedAccounts
    private val verifiedEvmAddresses = mutableSetOf<String>()

    init {
        AccountEmojiManager.addListener(this)
    }

    fun loadData() {
        refreshWalletList()
    }

    private fun refreshWalletList(refreshBalance: Boolean = true) {
        ioScope {
            val wallet = WalletManager.wallet() ?: return@ioScope
            val walletAddresses = wallet.accounts.mapNotNull { (chainId, accounts) ->
                val isCurrentChain = when (chainNetWorkString()) {
                    NETWORK_NAME_MAINNET -> chainId == ChainId.Mainnet
                    NETWORK_NAME_TESTNET -> chainId == ChainId.Testnet
                    else -> false
                }
                if (isCurrentChain) {
                    accounts.map { it.address.toAddress() }
                } else {
                    null
                }
            }.flatten()

            val addressList = mutableListOf<String>()
            val accounts = mutableListOf<WalletAccountData>()
            val pendingEvmAddresses = mutableListOf<Pair<String, String>>() // EVM address to wallet address mapping

            // Add EOA account if exists
            val eoaAddress = WalletManager.getEOAAddressCached()
            if (eoaAddress != null) {
                val emojiInfo = AccountEmojiManager.getEmojiByAddress(eoaAddress)
                addressList.add(eoaAddress)
                accounts.add(
                    WalletAccountData(
                        address = eoaAddress,
                        name = emojiInfo.emojiName,
                        emojiId = emojiInfo.emojiId,
                        isSelected = WalletManager.selectedWalletAddress() == eoaAddress,
                        isEOAAccount = true
                    )
                )
            }

            // Add wallet accounts
            walletAddresses.forEach { address ->
                val emojiInfo = AccountEmojiManager.getEmojiByAddress(address)
                val linkedAccounts = mutableListOf<LinkedAccountData>()

                // Add child accounts
                WalletManager.childAccountList(address)?.get()?.forEach { childAccount ->
                    addressList.add(childAccount.address)
                    linkedAccounts.add(
                        LinkedAccountData(
                            address = childAccount.address,
                            name = childAccount.name,
                            icon = childAccount.icon,
                            emojiId = AccountEmojiManager.getEmojiByAddress(childAccount.address).emojiId,
                            isSelected = WalletManager.selectedWalletAddress() == childAccount.address,
                            isCOAAccount = false
                        )
                    )
                }

                // Handle EVM address (COA)
                EVMWalletManager.getEVMAddressByAddress(address)?.let { evmAddress ->
                    addressList.add(evmAddress)
                    // Add to pending list for verification if not already verified
                    if (evmAddress !in verifiedEvmAddresses) {
                        pendingEvmAddresses.add(Pair(evmAddress, address))
                    } else {
                        // Add directly to linkedAccounts if already verified
                        val evmEmojiInfo = AccountEmojiManager.getEmojiByAddress(evmAddress)
                        linkedAccounts.add(
                            LinkedAccountData(
                                address = evmAddress,
                                name = evmEmojiInfo.emojiName,
                                icon = null,
                                emojiId = evmEmojiInfo.emojiId,
                                isSelected = WalletManager.selectedWalletAddress() == evmAddress,
                                isCOAAccount = true
                            )
                        )
                    }
                }

                accounts.add(
                    WalletAccountData(
                        address = address,
                        name = emojiInfo.emojiName,
                        emojiId = emojiInfo.emojiId,
                        isSelected = WalletManager.selectedWalletAddress() == address,
                        linkedAccounts = linkedAccounts,
                        isEOAAccount = false
                    )
                )
                addressList.add(address)
            }

            _accounts.value = accounts

            if (refreshBalance) {
                fetchAllBalances(addressList, pendingEvmAddresses)
            }
        }
    }

    private fun fetchAllBalances(addressList: List<String>, pendingEvmAddresses: List<Pair<String, String>> = emptyList()) {
        ioScope {
            val balanceMap = cadenceGetAllFlowBalance(addressList) ?: return@ioScope
            val formattedBalanceMap = balanceMap.mapValues { (_, balance) ->
                "${balance.formatLargeBalanceNumber(isAbbreviation = true)} FLOW"
            }
            _balanceMap.value = formattedBalanceMap

            // Check each pending EVM address
            pendingEvmAddresses.forEach { (evmAddress, walletAddress) ->
                val evmBalance = balanceMap[evmAddress]
                val hasBalance = evmBalance != null && evmBalance > BigDecimal.ZERO
                var hasNFTs = false

                if (!hasBalance) {
                    try {
                        val nftResponse = service.getEVMNFTCollections(evmAddress)
                        val totalNftCount = nftResponse.data?.sumOf { it.count ?: 0 } ?: 0
                        hasNFTs = nftResponse.data?.isNotEmpty() == true && totalNftCount > 0
                    } catch (e: Exception) {
                        // Ignore NFT API errors
                    }
                }

                if (hasBalance || hasNFTs) {
                    // Add EVM address to linked accounts
                    val currentAccounts = _accounts.value.toMutableList()
                    val walletAccount = currentAccounts.find { it.address == walletAddress }
                    walletAccount?.let { account ->
                        // Check if EVM address already exists in linked accounts
                        val alreadyExists = account.linkedAccounts.any { it.address == evmAddress }
                        if (!alreadyExists) {
                            val emojiInfo = AccountEmojiManager.getEmojiByAddress(evmAddress)
                            val updatedLinkedAccounts = account.linkedAccounts.toMutableList()
                            updatedLinkedAccounts.add(
                                LinkedAccountData(
                                    address = evmAddress,
                                    name = emojiInfo.emojiName,
                                    icon = null,
                                    emojiId = emojiInfo.emojiId,
                                    isSelected = WalletManager.selectedWalletAddress() == evmAddress,
                                    isCOAAccount = true
                                )
                            )
                            val updatedAccount = account.copy(linkedAccounts = updatedLinkedAccounts)
                            val accountIndex = currentAccounts.indexOfFirst { it.address == walletAddress }
                            if (accountIndex >= 0) {
                                currentAccounts[accountIndex] = updatedAccount
                                _accounts.value = currentAccounts
                            }
                        }
                        // Add to verified cache for future refreshWalletList calls
                        verifiedEvmAddresses.add(evmAddress)
                    }
                } else {
                    // Remove EVM address from linked accounts if it no longer has assets
                    val currentAccounts = _accounts.value.toMutableList()
                    val walletAccount = currentAccounts.find { it.address == walletAddress }
                    walletAccount?.let { account ->
                        val existingLinkedAccount = account.linkedAccounts.find { it.address == evmAddress }
                        if (existingLinkedAccount != null) {
                            val updatedLinkedAccounts = account.linkedAccounts.toMutableList()
                            updatedLinkedAccounts.removeAll { it.address == evmAddress }
                            val updatedAccount = account.copy(linkedAccounts = updatedLinkedAccounts)
                            val accountIndex = currentAccounts.indexOfFirst { it.address == walletAddress }
                            if (accountIndex >= 0) {
                                currentAccounts[accountIndex] = updatedAccount
                                _accounts.value = currentAccounts
                            }
                        }
                    }
                    // Remove from verified cache
                    verifiedEvmAddresses.remove(evmAddress)
                }
            }
        }
    }

    fun toggleAccountVisibility(address: String) {
        val userId = firebaseUid() ?: return
        AccountVisibilityManager.toggleAccountVisibility(userId, address)

        // Refresh the account list to reflect the new visibility state
        refreshWalletList(false)
    }

    override fun onEmojiUpdate(userName: String, address: String, emojiId: Int, emojiName: String) {
        refreshWalletList()
    }
}
