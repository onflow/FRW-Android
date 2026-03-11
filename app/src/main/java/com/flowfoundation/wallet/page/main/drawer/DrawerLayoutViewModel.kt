package com.flowfoundation.wallet.page.main.drawer

import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.emoji.OnEmojiUpdate
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceGetAllFlowBalance
import com.flowfoundation.wallet.manager.walletdata.FlowWallet
import com.flowfoundation.wallet.manager.walletdata.EOAWallet
import com.flowfoundation.wallet.manager.walletdata.ChildWallet
import com.flowfoundation.wallet.manager.walletdata.COAWallet
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.config.AppConfig
import com.flowfoundation.wallet.manager.key.AndroidKeystoreCryptoProvider
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.addNewFlowAccount
import com.flowfoundation.wallet.network.retrofitApi
import com.flowfoundation.wallet.utils.formatLargeBalanceNumber
import com.flowfoundation.wallet.utils.isHideCOAWithZeroBalanceEnable
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.manager.account.AccountVisibilityManager
import com.flowfoundation.wallet.manager.account.OnAccountUpdate
import com.flowfoundation.wallet.page.main.model.LinkedAccountData
import com.flowfoundation.wallet.page.main.model.WalletAccountData
import com.flowfoundation.wallet.utils.toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal

class DrawerLayoutViewModel : ViewModel(), OnAccountUpdate, OnEmojiUpdate {

    private val _userInfo = MutableStateFlow<UserInfoData?>(null)
    val userInfo: StateFlow<UserInfoData?> = _userInfo.asStateFlow()

    private val _showEvmLayout = MutableStateFlow(false)
    val showEvmLayout: StateFlow<Boolean> = _showEvmLayout.asStateFlow()

    private val _accounts = MutableStateFlow<List<WalletAccountData>>(emptyList())
    val accounts: StateFlow<List<WalletAccountData>> = _accounts.asStateFlow()

    private val _balanceMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val balanceMap: StateFlow<Map<String, String>> = _balanceMap.asStateFlow()

    private val _isAddingAccount = MutableStateFlow(false)
    val isAddingAccount: StateFlow<Boolean> = _isAddingAccount.asStateFlow()

    private val _canAddAccount = MutableStateFlow(false)
    val canAddAccount: StateFlow<Boolean> = _canAddAccount.asStateFlow()

    private val service by lazy { retrofitApi().create(ApiService::class.java) }

    // Cache for verified EVM addresses that should be included in linkedAccounts
    private val verifiedEvmAddresses = mutableSetOf<String>()

    init {
        AccountManager.addListener(this)
        AccountEmojiManager.addListener(this)
    }

    fun loadData(refreshBalance: Boolean = false) {
        refreshWalletList(refreshBalance)
    }

    private fun loadEvmStatus() {
        _showEvmLayout.value = WalletManager.isEVMAccountSelected().not() && EVMWalletManager.showEVMEnablePage()
    }

    fun refreshWalletList(refreshBalance: Boolean = false) {
        ioScope {
            _userInfo.value = AccountManager.userInfo() ?: return@ioScope
            val currentNetwork = chainNetWorkString()
            val walletNodes = AccountManager.walletNodes() ?: return@ioScope

            val addressList = mutableListOf<String>()
            val accounts = mutableListOf<WalletAccountData>()
            val pendingEvmAddresses = mutableListOf<Pair<String, String>>() // EVM address to wallet address mapping

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
                                        if (evmAddress !in verifiedEvmAddresses) {
                                            pendingEvmAddresses.add(Pair(evmAddress, mainNode.address))
                                        } else {
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
                            }

                            accounts.add(
                                WalletAccountData(
                                    address = mainNode.address,
                                    name = emojiInfo.emojiName,
                                    emojiId = emojiInfo.emojiId,
                                    isSelected = WalletManager.selectedWalletAddress().equals(mainNode.address, ignoreCase = true),
                                    linkedAccounts = linkedAccounts
                                )
                            )
                            addressList.add(mainNode.address)
                        }
                    }
                }
            }

            // Filter out hidden accounts for the current user.
            // Use AccountManager (already updated before listeners fire) rather than
            // firebaseUid() (reads Firebase.auth.currentUser which can still be anonymous
            // during the auth transition window of an account switch).
            val userId = AccountManager.get()?.wallet?.id
            val filteredAccounts = if (userId != null) {
                AccountVisibilityManager.filterVisibleAccounts(
                    userId,
                    accounts
                ) { it.address }
            } else {
                accounts
            }

            _accounts.value = filteredAccounts
            loadEvmStatus()

            val flowWalletCount = walletNodes.filterIsInstance<FlowWallet>()
                .count { it.chainIdString == currentNetwork }
            val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()
            _canAddAccount.value = AppConfig.canCreateNewAccount()
                && flowWalletCount < 5
                && cryptoProvider != null
                && cryptoProvider !is AndroidKeystoreCryptoProvider

            if (refreshBalance) {
                fetchAllBalances(addressList, pendingEvmAddresses)
            }
        }
    }

    fun addAccount() {
        ioScope {
            _isAddingAccount.value = true
            try {
                val result = addNewFlowAccount()
                if (result == null) {
                    _isAddingAccount.value = false
                    toast(msgRes = R.string.common_error_hint)
                } else {
                    refreshWalletList(false)
                }
            } finally {
                _isAddingAccount.value = false
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

            val hideCOAWithZeroBalance = isHideCOAWithZeroBalanceEnable()

            // Check each pending EVM address
            pendingEvmAddresses.forEach { (evmAddress, walletAddress) ->
                val shouldShow = if (!hideCOAWithZeroBalance) {
                    true
                } else {
                    val evmBalance = balanceMap[evmAddress]
                    val hasBalance = evmBalance != null && evmBalance > BigDecimal.ZERO
                    var hasNFTs = false

                    if (!hasBalance) {
                        try {
                            val nftResponse = service.getEVMNFTCollections(evmAddress)
                            val totalNftCount = nftResponse.data?.sumOf { it.count ?: 0 } ?: 0
                            hasNFTs = nftResponse.data?.isNotEmpty() == true && totalNftCount > 0
                        } catch (_: Exception) {
                            // Ignore NFT API errors
                        }
                    }

                    hasBalance || hasNFTs
                }

                if (shouldShow) {
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

    override fun onEmojiUpdate(userName: String, address: String, emojiId: Int, emojiName: String) {
        refreshWalletList()
    }

    override fun onAccountUpdate(account: Account) {
        refreshWalletList(true)
    }
}
