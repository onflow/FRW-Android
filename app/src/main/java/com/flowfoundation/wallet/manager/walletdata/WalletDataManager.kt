package com.flowfoundation.wallet.manager.walletdata

import com.flow.wallet.wallet.Wallet
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.app.toNetworkString
import com.flowfoundation.wallet.manager.childaccount.ChildAccount
import com.flowfoundation.wallet.manager.childaccount.parseAccountMetas
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
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
            val selectedAddress = WalletManager.getCurrentFlowWalletAddress()

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
            val selectedAddress = WalletManager.getCurrentFlowWalletAddress()

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
        val existingWallet = WalletManager.wallet()
        val wallet: Wallet?
        val canDeriveEoa: Boolean

        if (existingWallet == null) {
            logd(TAG, "WalletManager.wallet() is null, attempting to create temporary instance")
            val result = WalletCreationHelper.createWalletFromAccount(currentAccount)
            wallet = result?.wallet
            canDeriveEoa = result?.canDeriveEoa ?: false
        } else {
            logd(TAG, "Reusing WalletManager instance: ${WalletManager.getCurrentFlowWalletAddress()}")
            wallet = existingWallet
            canDeriveEoa = WalletManager.canDeriveEoa()
        }

        if (wallet != null) {
            updateCurrentAccountData(currentAccount, wallet, canDeriveEoa)
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
    private suspend fun updateCurrentAccountData(account: Account, wallet: Wallet, canDeriveEoa: Boolean) {
        try {
            logd(TAG, "Refreshing wallet accounts for ${account.userInfo.username}...")
            wallet.refreshAccounts()
            logd(TAG, "wallet.refreshAccounts() done. Internal accounts: ${wallet.accounts.map { "${it.key}: ${it.value.size}" }}")

            // 1. Fetch data from Indexer
            val indexerBlockchainData = fetchWalletListData(wallet)
            logd(TAG, "Indexer discovered ${indexerBlockchainData.size} wallets: ${indexerBlockchainData.map { "${it.address} (${it.chainId})" }}")

            // 2. Get data from Backend (preserved in account.wallet)
            val backendBlockchainData = account.wallet?.wallets?.flatMap { w ->
              w.blockchain?.map { b ->
                BlockchainData(address = b.address, chainId = b.chainId)
              } ?: emptyList()
            } ?: emptyList()
            logd(TAG, "Backend record has ${backendBlockchainData.size} wallets: ${backendBlockchainData.map { "${it.address} (${it.chainId})" }}")

            // 3. Merge both sources to avoid losing networks (like Testnet) when indexer is slow
            val allBlockchainData = (indexerBlockchainData + backendBlockchainData)
                .distinctBy { "${it.address}-${it.chainId}" }
                .filter { it.address.isNotBlank() }

            logd(TAG, "Merged blockchain data: ${allBlockchainData.map { "${it.address} (${it.chainId})" }}")

            // Build Wallet Nodes
            val nodes = mutableListOf<MainWallet>()

            logd(TAG, "Fetching data for ${allBlockchainData.size} BlockchainData entries for node construction")
            fun getEmojiInfo(address: String) = AccountEmojiManager.getEmojiByAddress(address)

            // EOA Wallet - canDeriveEoa is determined per-wallet by WalletCreationHelper
            // based on key type (Secure Enclave / private key = false, seed phrase = true)
            if (canDeriveEoa) {
                val eoa = deriveEoaAddress(wallet)
                logd(TAG, "Generated EOA address: $eoa")
                if (eoa.isNotEmpty()) {
                    logd(TAG, "Adding EOA for account: $eoa")
                    val eoaEmojiInfo = getEmojiInfo(eoa)
                    nodes.add(EOAWallet(
                        address = eoa,
                        name = eoaEmojiInfo.emojiName,
                        emojiId = eoaEmojiInfo.emojiId
                    ))
                }
            }

            kotlinx.coroutines.supervisorScope {
                val deferredFlowNodes = allBlockchainData.map { blockchainData ->
                    val address = blockchainData.address
                    val chainId = blockchainData.chainId

                    async {
                        // Find existing node if any to preserve existing linked wallets
                        val existingNode = account.walletNodes.filterIsInstance<FlowWallet>()
                            .firstOrNull { it.address == address && it.chainIdString == chainId }

                        val linkedWallets = mutableListOf<LinkedWallet>()

                        // Start with existing linked wallets to prevent flickering/loss on error
                        existingNode?.linkedWallets?.let { linkedWallets.addAll(it) }

                        try {
                            // Update Child Accounts
                            val children = fetchChildAccountsForAddress(address)
                            // Only update if we successfully fetched something or if we know for sure it's empty
                            // (Here assuming fetchChildAccountsForAddress returns emptyList on error,
                            // but we might want to check log logs. For now, strict replacement is risky without error diff.
                            // Better strategy: replace specific types only if fetch succeeds)

                            if (children.isNotEmpty()) {
                                val nonChildLinks = linkedWallets.filter { it !is ChildWallet }
                                val newChildren = children.map { child ->
                                    ChildWallet(
                                        address = child.address,
                                        name = child.name,
                                        icon = child.icon,
                                        emojiId = getEmojiInfo(child.address).emojiId
                                    )
                                }
                                linkedWallets.clear()
                                linkedWallets.addAll(nonChildLinks + newChildren)
                            }

                            // Update COA
                            val coa = fetchEVMAddressForAddress(address)
                            if (coa != null) {
                                val nonCoaLinks = linkedWallets.filter { it !is COAWallet }
                                val coaEmojiInfo = getEmojiInfo(coa)
                                linkedWallets.clear()
                                linkedWallets.addAll(nonCoaLinks + COAWallet(
                                    address = coa,
                                    name = coaEmojiInfo.emojiName,
                                    emojiId = coaEmojiInfo.emojiId
                                ))
                                logd(TAG, "Updated COA for $address: $coa")
                            }
                        } catch (e: Exception) {
                            logd(TAG, "Error updating linked data for $address: ${e.message}")
                            // Keep existing linkedWallets on error
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
                nodes.addAll(deferredFlowNodes.awaitAll())
            }

            logd(TAG, "Final wallet nodes built: ${nodes.size}")

            // Persist changes to AccountManager
            AccountManager.updateCurrentAccount { it.copy(walletNodes = nodes) }
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

            val result = WalletCreationHelper.createWalletFromAccount(account, false)
            val wallet = result?.wallet
            val canDeriveEoa = result?.canDeriveEoa ?: false
            if (wallet != null) {
                logd(TAG, "Refreshing wallet accounts for non-current account ${account.userInfo.username}...")
                wallet.refreshAccounts()

                val walletList = fetchWalletListData(wallet)

                // Build Wallet Nodes
                val nodes = mutableListOf<MainWallet>()
                val accountUsername = account.userInfo.username
                val accountEmojiList = (account.walletEmojiList ?: emptyList()).toMutableList()
                fun getEmojiInfo(address: String) = AccountEmojiManager.getEmojiByAddressForAccount(
                    address, accountUsername, accountEmojiList
                )

                // EOA - canDeriveEoa is determined per-wallet by WalletCreationHelper
                // based on key type (Secure Enclave / private key = false, seed phrase = true)
                if (canDeriveEoa) {
                    val eoa = deriveEoaAddress(wallet)
                    if (eoa.isNotEmpty()) {
                        logd(TAG, "Adding EOA for non-current account: $eoa")
                        val eoaEmojiInfo = getEmojiInfo(eoa)
                        nodes.add(EOAWallet(
                            address = eoa,
                            name = eoaEmojiInfo.emojiName,
                            emojiId = eoaEmojiInfo.emojiId
                        ))
                    }
                } else {
                    logd(TAG, "Skipping EOA for non-current account (canDeriveEoa=false)")
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
                logd(TAG, "fetchEVMAddressForAddress: raw evmAddress from Cadence: $evmAddress")
                val formatedAddress = evmAddress.toAddress()
                logd(TAG, "fetchEVMAddressForAddress: formattedAddress: $formatedAddress")

                if (EVMWalletManager.isValidEVMAddress(formatedAddress)) {
                    val checksumAddress = EVMWalletManager.toChecksumEVMAddress(formatedAddress)
                    logd(TAG, "fetchEVMAddressForAddress: valid checksum address: $checksumAddress")
                    checksumAddress
                } else {
                    logd(TAG, "fetchEVMAddressForAddress: Invalid EVM address format: $formatedAddress")
                    null
                }
            } else {
                logd(TAG, "fetchEVMAddressForAddress: Cadence returned empty/null for $address")
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
