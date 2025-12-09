package com.flowfoundation.wallet.manager.walletdata

import com.flow.wallet.wallet.Wallet
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.AccountType
import com.flowfoundation.wallet.manager.app.toNetworkString
import com.flowfoundation.wallet.manager.childaccount.ChildAccount
import com.flowfoundation.wallet.manager.childaccount.parseAccountMetas
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import org.onflow.flow.models.SigningAlgorithm
import com.flowfoundation.wallet.manager.flowjvm.CadenceScript
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryEVMAddress
import com.flowfoundation.wallet.manager.flowjvm.executeCadence
import com.flowfoundation.wallet.manager.wallet.WalletCreationHelper
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.model.BlockchainData
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.wallet.toAddress
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.onflow.flow.infrastructure.Cadence

/**
 * WalletDataManager handles Account data updates (eoaAddress, childWalletData, etc.)
 * Uses single memory-based timestamp with 30-minute expiration
 * Prioritizes current account data and gets currentWallet from WalletManager
 * This class assumes ChildAccount, parseAccountMetas, CadenceScript, executeCadence are properly imported.
 */
object WalletDataManager {
    private val TAG = "WalletDataManager"
    private const val CACHE_DURATION_MS = 30 * 60 * 1000L // 30 minutes

    // Single timestamp for all wallet data updates
    private var lastUpdateTime: Long = 0L

    /**
     * Check if cached data is still valid (within 30 minutes)
     */
    private fun isCacheValid(): Boolean {
        val currentTime = System.currentTimeMillis()
        val isValid = (currentTime - lastUpdateTime) < CACHE_DURATION_MS
        logd(TAG, "Cache validity check: ${if (isValid) "VALID" else "EXPIRED"} (${(currentTime - lastUpdateTime) / 1000}s ago)")
        return isValid
    }

    /**
     * Refresh only child accounts for the current selected address
     */
    fun refreshCurrentAccountChildAccounts() {
        logd(TAG, "Refreshing child accounts for current selected address")

        ioScope {
            val selectedAddress = WalletManager.getFlowWalletAddress()

            if (selectedAddress.isNullOrBlank()) return@ioScope

            try {
                logd(TAG, "Fetching child accounts for address: $selectedAddress")

                val newChildAccounts = fetchChildAccountsForAddress(selectedAddress)

                AccountManager.updateCurrentAccount { currentAccount ->
                    val updatedNodes = currentAccount.walletNodes.map { node ->
                        if (node is FlowWallet && node.address == selectedAddress) {
                            val otherLinks = node.linkedWallets.filter { it !is ChildWallet }
                            val newChildren = newChildAccounts.map { child ->
                                ChildWallet(
                                    address = child.address,
                                    name = child.name,
                                    icon = child.icon,
                                    emojiId = AccountEmojiManager.getEmojiByAddress(child.address).emojiId
                                )
                            }
                            node.copy(linkedWallets = otherLinks + newChildren)
                        } else {
                            node
                        }
                    }
                    logd(TAG, "Child wallet data updated for $selectedAddress: ${newChildAccounts.size} child accounts")
                    currentAccount.copy(walletNodes = updatedNodes)
                }
            } catch (e: Exception) {
                logd(TAG, "Error refreshing child accounts: ${e.message}")
            }
        }
    }

    /**
     * Refresh EVM address for the current selected address
     */
    fun refreshCurrentAccountEVMAddress(callback: (String?) -> Unit) {
        logd(TAG, "Refreshing EVM address for current selected address")

        ioScope {
            val selectedAddress = WalletManager.getFlowWalletAddress()

            if (selectedAddress.isNullOrBlank()) {
                callback(null)
                return@ioScope
            }

            try {
                logd(TAG, "Fetching EVM address for address: $selectedAddress")
                val evmAddress = fetchEVMAddressForAddress(selectedAddress)

                if (!evmAddress.isNullOrBlank()) {
                    AccountManager.updateCurrentAccount { currentAccount ->
                        val emojiInfo = AccountEmojiManager.getEmojiByAddress(evmAddress)
                        val updatedNodes = currentAccount.walletNodes.map { node ->
                            if (node is FlowWallet && node.address == selectedAddress) {
                                val otherLinks = node.linkedWallets.filter { it !is COAWallet }
                                val newCoa = COAWallet(
                                    address = evmAddress,
                                    name = emojiInfo.emojiName,
                                    emojiId = emojiInfo.emojiId
                                )
                                node.copy(linkedWallets = otherLinks + newCoa)
                            } else {
                                node
                            }
                        }
                        logd(TAG, "EVM address updated for $selectedAddress: $evmAddress")
                        currentAccount.copy(walletNodes = updatedNodes)
                    }
                    callback(evmAddress)
                } else {
                    logd(TAG, "No EVM address found for $selectedAddress")
                    callback(null)
                }
            } catch (e: Exception) {
                logd(TAG, "Error refreshing EVM address: ${e.message}")
                callback(null)
            }
        }
    }

    /**
     * Update current account data
     */
    suspend fun updateCurrentAccount() {
        val currentAccount = AccountManager.get() ?: return
        logd(TAG, "Updating current account data: ${currentAccount.userInfo.username}")

        // Try to reuse the singleton Wallet instance from WalletManager
        var wallet = WalletManager.wallet()

        if (wallet == null) {
            logd(TAG, "WalletManager.wallet() is null, attempting to create temporary instance")
            wallet = WalletCreationHelper.createWalletFromAccount(currentAccount)
        } else {
            logd(TAG, "Reusing WalletManager instance: ${WalletManager.getFlowWalletAddress()}")
        }

        if (wallet != null) {
            updateCurrentAccountData(currentAccount, wallet)
        } else {
            logd(TAG, "Failed to obtain wallet instance for current account")
        }
    }

    /**
     * Update all wallet data if cache is expired
     * Prioritizes current account data and uses WalletManager's currentWallet
     * This method should be called on MainActivity.onCreate() (triggered by relaunch)
     */
    fun updateWalletData() {
        if (isCacheValid()) {
            logd(TAG, "All wallet data cache is still valid, skipping update")
            return
        }

        logd(TAG, "Updating all wallet data - cache expired")

        ioScope {
            try {
                // Step 1: Priority update current account
                updateCurrentAccount()

                val currentAccount = AccountManager.get()
                val allAccounts = AccountManager.list()

                // Step 2: Update other accounts concurrently
                val otherAccounts = allAccounts.filter { it.userInfo.username != currentAccount?.userInfo?.username }
                if (otherAccounts.isNotEmpty()) {
                    logd(TAG, "Updating ${otherAccounts.size} other accounts concurrently")

                    kotlinx.coroutines.supervisorScope {
                        val deferredUpdates = otherAccounts.map { account ->
                            async { updateNonCurrentAccountData(account) }
                        }

                        val updatedAccounts = deferredUpdates.awaitAll().filterNotNull()
                        if (updatedAccounts.isNotEmpty()) {
                            AccountManager.updateAccountList(updatedAccounts)
                        }
                    }
                }

                // Step 3: Update unified cache timestamp
                lastUpdateTime = System.currentTimeMillis()
                logd(TAG, "All wallet data updates completed successfully")

            } catch (e: Exception) {
                logd(TAG, "Error during wallet data update: ${e.message}")
            }
        }
    }

    /**
     * Force update all data (ignore cache timestamp)
     */
    fun forceUpdateAllWalletData() {
        logd(TAG, "Force updating all wallet data (ignoring cache)")

        // Reset cache timestamp to force updates
        lastUpdateTime = 0L

        updateWalletData()
    }

    /**
     * Clear cache timestamp
     */
    fun clearCache() {
        logd(TAG, "Clearing wallet data cache")
        lastUpdateTime = 0L
    }

    /**
     * Update data for the current account using provided Wallet
     */
    private suspend fun updateCurrentAccountData(account: Account, wallet: Wallet) { // Method name changed
        try {
            logd(TAG, "Refreshing wallet accounts for ${account.userInfo.username}...")
            wallet.refreshAccounts()

            // Fetch all BlockchainData directly
            val allBlockchainData = fetchWalletListData(wallet)

            // Build Wallet Nodes
            val nodes = mutableListOf<MainWallet>()

            logd(TAG, "Fetching data for ${allBlockchainData.size} BlockchainData entries for node construction")
            fun getEmojiInfo(address: String) = AccountEmojiManager.getEmojiByAddress(address)

            // EOA Wallet - only for "full" (Recovery Phrase) accounts, not "hardware" (Secure Enclave)
            // For legacy accounts (accountType = null), fall back to checking signature algorithm
            val canDeriveEoa = when (account.accountType) {
                AccountType.HARDWARE -> false  // Secure Enclave - no EOA
                AccountType.FULL -> true       // Recovery Phrase - derive EOA
                else -> {
                    // Legacy account - check signature algorithm as fallback
                    try {
                        val cryptoProvider = CryptoProviderManager.generateAccountCryptoProvider(account)
                        val isP256 = cryptoProvider?.getSignatureAlgorithm() == SigningAlgorithm.ECDSA_P256
                        !isP256  // P256 = Secure Enclave = no EOA
                    } catch (e: Exception) {
                        logd(TAG, "Could not determine account type, assuming can derive EOA: ${e.message}")
                        true  // Default to allowing EOA for legacy accounts
                    }
                }
            }
            if (!WalletManager.isEoaDisabled() && canDeriveEoa) {
                val eoa = deriveEoaAddress(wallet)
                if (eoa.isNotEmpty()) {
                    logd(TAG, "Adding EOA for account (accountType=${account.accountType}): $eoa")
                    val eoaEmojiInfo = getEmojiInfo(eoa)
                    nodes.add(EOAWallet(
                        address = eoa,
                        name = eoaEmojiInfo.emojiName,
                        emojiId = eoaEmojiInfo.emojiId
                    ))
                }
            } else {
                logd(TAG, "Skipping EOA derivation (accountType=${account.accountType}, isEoaDisabled=${WalletManager.isEoaDisabled()})")
            }

            kotlinx.coroutines.supervisorScope {
                val deferredFlowNodes = allBlockchainData.mapNotNull { blockchainData ->
                    val address = blockchainData.address
                    val chainId = blockchainData.chainId

                    if (address.isBlank()) {
                        null
                    } else {
                        async {
                            val linkedWallets = mutableListOf<LinkedWallet>()

                            // Child Accounts
                            val children = fetchChildAccountsForAddress(address)
                            children.forEach { child ->
                                linkedWallets.add(ChildWallet(
                                    address = child.address,
                                    name = child.name,
                                    icon = child.icon,
                                    emojiId = getEmojiInfo(child.address).emojiId
                                ))
                            }

                            // COA
                            val coa = fetchEVMAddressForAddress(address)
                            if (coa != null) {
                               val coaEmojiInfo = getEmojiInfo(coa)
                               linkedWallets.add(COAWallet(
                                    address = coa,
                                    name = coaEmojiInfo.emojiName,
                                    emojiId = coaEmojiInfo.emojiId
                                ))
                            }
                            val emojiInfo = getEmojiInfo(address)
                            FlowWallet(
                                address = address,
                                name = emojiInfo.emojiName,
                                emojiId = emojiInfo.emojiId,
                                chainIdString = chainId,
                                linkedWallets = linkedWallets
                            )
                        }
                    }
                }
                nodes.addAll(deferredFlowNodes.awaitAll())
            }

            logd(TAG, "Wallet nodes built: ${nodes.size}")

            // Check if we found any FlowWallets from the key indexer
            val newFlowWallets = nodes.filterIsInstance<FlowWallet>()
            val existingFlowWallets = account.walletNodes.filterIsInstance<FlowWallet>()
            
            // If we didn't find any FlowWallets from key indexer but account already has some,
            // preserve the existing ones (key indexer may not have indexed new accounts yet)
            // BUT also query for COA on preserved FlowWallets that don't have linkedWallets yet
            val finalNodes = if (newFlowWallets.isEmpty() && existingFlowWallets.isNotEmpty()) {
                logd(TAG, "No FlowWallets found from key indexer, preserving ${existingFlowWallets.size} existing FlowWallets")
                
                // Query COA for preserved FlowWallets that have empty linkedWallets
                val updatedExistingWallets = existingFlowWallets.map { flowWallet ->
                    if (flowWallet.linkedWallets.isEmpty()) {
                        try {
                            val coa = fetchEVMAddressForAddress(flowWallet.address)
                            if (coa != null) {
                                logd(TAG, "Found COA for preserved FlowWallet ${flowWallet.address}: $coa")
                                val coaEmojiInfo = getEmojiInfo(coa)
                                flowWallet.copy(linkedWallets = listOf(COAWallet(
                                    address = coa,
                                    name = coaEmojiInfo.emojiName,
                                    emojiId = coaEmojiInfo.emojiId
                                )))
                            } else {
                                logd(TAG, "No COA found for preserved FlowWallet ${flowWallet.address}")
                                flowWallet
                            }
                        } catch (e: Exception) {
                            logd(TAG, "Error querying COA for preserved FlowWallet ${flowWallet.address}: ${e.message}")
                            flowWallet
                        }
                    } else {
                        flowWallet
                    }
                }
                nodes + updatedExistingWallets
            } else {
                nodes
            }

            logd(TAG, "Final wallet nodes: ${finalNodes.size} (${finalNodes.filterIsInstance<FlowWallet>().size} FlowWallets, ${finalNodes.filterIsInstance<EOAWallet>().size} EOAWallets)")

            // Persist changes to AccountManager (always use updateCurrentAccount as it's for current)
            AccountManager.updateCurrentAccount { it.copy(walletNodes = finalNodes) }
            logd(TAG, "Updated current account data for ${account.userInfo.username}")

        } catch (e: Exception) {
            logd(TAG, "Error updating current account data for ${account.userInfo.username}: ${e.message}")
        }
    }

    /**
     * Update data for non-current accounts by creating their wallets
     * Returns the updated account or null if failed
     */
    private suspend fun updateNonCurrentAccountData(account: Account): Account? {
        return try {
            logd(TAG, "Updating non-current account: ${account.userInfo.username}")

            val wallet = WalletCreationHelper.createWalletFromAccount(account)
            if (wallet != null) {
                logd(TAG, "Refreshing wallet accounts for non-current account ${account.userInfo.username}...")
                wallet.refreshAccounts()

                val walletList = fetchWalletListData(wallet)

                // Build Wallet Nodes
                val nodes = mutableListOf<MainWallet>()
                fun getEmojiInfo(address: String) = AccountEmojiManager.getEmojiByAddress(address)
                
                // EOA - only for "full" (Recovery Phrase) accounts, not "hardware" (Secure Enclave)
                // For legacy accounts (accountType = null), fall back to checking signature algorithm
                val canDeriveEoa = when (account.accountType) {
                    AccountType.HARDWARE -> false  // Secure Enclave - no EOA
                    AccountType.FULL -> true       // Recovery Phrase - derive EOA
                    else -> {
                        // Legacy account - check signature algorithm as fallback
                        try {
                            val cryptoProvider = CryptoProviderManager.generateAccountCryptoProvider(account)
                            val isP256 = cryptoProvider?.getSignatureAlgorithm() == SigningAlgorithm.ECDSA_P256
                            !isP256  // P256 = Secure Enclave = no EOA
                        } catch (e: Exception) {
                            logd(TAG, "Could not determine account type for ${account.userInfo.username}: ${e.message}")
                            true  // Default to allowing EOA for legacy accounts
                        }
                    }
                }
                if (!WalletManager.isEoaDisabled() && canDeriveEoa) {
                    val eoa = deriveEoaAddress(wallet)
                    if (eoa.isNotEmpty()) {
                        logd(TAG, "Adding EOA for non-current account (accountType=${account.accountType}): $eoa")
                        val eoaEmojiInfo = getEmojiInfo(eoa)
                        nodes.add(EOAWallet(
                            address = eoa,
                            name = eoaEmojiInfo.emojiName,
                            emojiId = eoaEmojiInfo.emojiId
                        ))
                    }
                } else {
                    logd(TAG, "Skipping EOA for non-current account (accountType=${account.accountType})")
                }

                kotlinx.coroutines.supervisorScope {
                    val deferredNodes = walletList.map { data ->
                        async {
                            val linkedWallets = mutableListOf<LinkedWallet>()

                            // Child Accounts
                            val children = fetchChildAccountsForAddress(data.address)
                            children.forEach { child ->
                                linkedWallets.add(ChildWallet(
                                    address = child.address,
                                    name = child.name,
                                    icon = child.icon,
                                    emojiId = getEmojiInfo(child.address).emojiId
                                ))
                            }

                            // COA
                            val coa = fetchEVMAddressForAddress(data.address)
                            if (coa != null) {
                                val coaEmojiInfo = getEmojiInfo(coa)
                                linkedWallets.add(COAWallet(
                                    address = coa,
                                    name = coaEmojiInfo.emojiName,
                                    emojiId = coaEmojiInfo.emojiId
                                ))
                            }
                            val emojiInfo = getEmojiInfo(data.address)
                            FlowWallet(
                                address = data.address,
                                name = emojiInfo.emojiName,
                                emojiId = emojiInfo.emojiId,
                                chainIdString = data.chainId,
                                linkedWallets = linkedWallets
                            )
                        }
                    }
                    nodes.addAll(deferredNodes.awaitAll())
                }

                account.walletNodes = nodes
                account
            } else {
                logd(TAG, "Failed to create wallet for account: ${account.userInfo.username}")
                null
            }
        } catch (e: Exception) {
            logd(TAG, "Error updating non-current account ${account.userInfo.username}: ${e.message}")
            null
        }
    }

    /**
     * Derive EOA address from Wallet
     */
    private suspend fun deriveEoaAddress(wallet: Wallet): String {
        return try {
            // Use wallet's built-in EOA address generation
            wallet.ethAddress(0)
        } catch (e: Exception) {
            logd(TAG, "Error generating EOA address: ${e.message}")
            ""
        }
    }

    /**
     * Fetch child accounts for a specific Address
     */
    private suspend fun fetchChildAccountsForAddress(address: String): List<ChildAccount> {
        if (address.isEmpty()) return emptyList()

        return try {
            // Run the cadence script to fetch child accounts
            val result = CadenceScript.CADENCE_QUERY_CHILD_ACCOUNT_META.executeCadence {
                arg { Cadence.address(address) }
            }
            result?.encode()?.parseAccountMetas().orEmpty()
        } catch (e: Exception) {
            logd(TAG, "Error fetching child accounts for $address: ${e.message}")
            emptyList()
        }
    }

    /**
     * Fetch EVM address for a specific Flow address
     */
    private suspend fun fetchEVMAddressForAddress(address: String): String? {
        if (address.isEmpty()) return null

        return try {
            val evmAddress = cadenceQueryEVMAddress(address)
            if (!evmAddress.isNullOrBlank()) {
                val formatedAddress = evmAddress.toAddress()
                if (EVMWalletManager.isValidEVMAddress(formatedAddress)) {
                    EVMWalletManager.toChecksumEVMAddress(formatedAddress)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            logd(TAG, "Error fetching EVM address for $address: ${e.message}")
            null
        }
    }

    private suspend fun fetchWalletListData(wallet: Wallet): List<BlockchainData> {
        logd(TAG, "fetchWalletListData: Waiting for wallet accounts...")

        val accounts = try {
            withTimeout(10000) {
                wallet.accountsFlow.first { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            logd(TAG, "Timeout or error waiting for accounts: ${e.message}")
            wallet.accounts
        }

        val allBlockchainData = accounts.flatMap { (chainId, flowAccounts) ->
            flowAccounts.map { account ->
                BlockchainData(address = account.address, chainId = chainId.toNetworkString())
            }
        }.filter { it.address.isNotBlank() }

        logd(TAG, "fetchWalletListData: BlockchainData loaded: ${allBlockchainData.size} entries")
        allBlockchainData.forEach {
            logd(TAG, "  Address: ${it.address}, ChainId: ${it.chainId}")
        }

        return allBlockchainData
    }
}
