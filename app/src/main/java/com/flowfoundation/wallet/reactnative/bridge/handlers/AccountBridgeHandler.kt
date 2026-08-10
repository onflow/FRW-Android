package com.flowfoundation.wallet.reactnative.bridge.handlers

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import com.flowfoundation.wallet.cache.recentTransactionCache
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.currentKeyId
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.walletdata.FlowWallet
import com.flowfoundation.wallet.manager.walletdata.COAWallet
import com.flowfoundation.wallet.manager.walletdata.ChildWallet
import com.flowfoundation.wallet.manager.walletdata.EOAWallet
import com.flowfoundation.wallet.reactnative.bridge.RNBridge
import com.flowfoundation.wallet.reactnative.bridge.createEmojiInfo
import com.flowfoundation.wallet.reactnative.bridge.isSelectedWalletAddress
import com.flowfoundation.wallet.utils.getWatchCollectibleAddress
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logw
import com.flowfoundation.wallet.utils.uiScope
import org.onflow.flow.models.FlowAddress

/**
 * Handler for account-related bridge methods
 * Handles: address retrieval, contacts, wallet accounts, profiles, signing key
 */
class AccountBridgeHandler(private val reactContext: ReactApplicationContext) {

    private val TAG = "AccountBridgeHandler"

    fun getSelectedAddress(): String? {
        return try {
            val address = WalletManager.selectedWalletAddress()
            logd(TAG, "getSelectedAddress() called, returning: $address")
            address
        } catch (e: Exception) {
            loge(TAG, "getSelectedAddress() error: ${e.message}")
            null
        }
    }

    fun getDebugAddress(): String? {
        return try {
            val watchAddress = getWatchCollectibleAddress()
            val resultAddress = watchAddress.ifEmpty {
                null
            }

            logd(TAG, "getDebugAddress() called, watchAddress: '$watchAddress', returning: $resultAddress")
            resultAddress
        } catch (e: Exception) {
            loge(TAG, "getDebugAddress() error: ${e.message}")
            null
        }
    }

    fun getRecentContacts(promise: Promise, bridgeModelToWritableMap: (Any) -> WritableMap) {
        ioScope {
            try {
                val recentData = recentTransactionCache().read()?.contacts

                val bridgeContacts = if (!recentData.isNullOrEmpty()) {
                    recentData.map { contact ->
                        RNBridge.Contact(
                            id = contact.id ?: contact.uniqueId(),
                            name = contact.name(),
                            address = contact.address ?: "",
                            avatar = contact.avatar,
                            username = contact.username,
                            contactName = contact.contactName
                        )
                    }
                } else {
                    emptyList()
                }

                val response = RNBridge.RecentContactsResponse(contacts = bridgeContacts)
                val result = bridgeModelToWritableMap(response)

                uiScope {
                    promise.resolve(result)
                }
            } catch (e: Exception) {
                val emptyResponse = RNBridge.RecentContactsResponse(contacts = emptyList())
                val result = bridgeModelToWritableMap(emptyResponse)
                uiScope {
                    promise.resolve(result)
                }
            }
        }
    }

    fun getWalletAccounts(promise: Promise, bridgeModelToWritableMap: (Any) -> WritableMap) {
        ioScope {
            try {
                val bridgeAccounts = mutableListOf<RNBridge.WalletAccount>()
                val currentAccount = AccountManager.get() ?: run {
                    val emptyResponse = RNBridge.WalletAccountsResponse(accounts = emptyList())
                    uiScope { promise.resolve(bridgeModelToWritableMap(emptyResponse)) }
                    return@ioScope
                }

                val currentNetwork = com.flowfoundation.wallet.manager.app.chainNetWorkString()

                // 1. Process FlowWallets (main account + linked wallets)
                val flowWallets = currentAccount.walletNodes.filterIsInstance<FlowWallet>()
                    .filter { it.chainIdString == currentNetwork }

                flowWallets.forEach { flowWallet ->
                    val mainAddress = flowWallet.address
                    val mainEmojiInfo = createEmojiInfo(mainAddress)

                    // Main account
                    bridgeAccounts.add(RNBridge.WalletAccount(
                        id = "main",
                        name = mainEmojiInfo?.name ?: "Main Account",
                        address = mainAddress,
                        emojiInfo = mainEmojiInfo,
                        parentEmoji = null,
                        parentAddress = null,
                        avatar = null,
                        isActive = isSelectedWalletAddress(mainAddress),
                        type = RNBridge.AccountType.MAIN,
                        balance = null,
                        nfts = null,
                    ))

                    // Linked wallets (COA/EVM + Child)
                    flowWallet.linkedWallets.forEach { linkedWallet ->
                        when (linkedWallet) {
                            is COAWallet -> {
                                val evmEmojiInfo = createEmojiInfo(linkedWallet.address)
                                bridgeAccounts.add(RNBridge.WalletAccount(
                                    id = "evm",
                                    name = evmEmojiInfo?.name ?: linkedWallet.name,
                                    address = linkedWallet.address,
                                    parentAddress = mainAddress,
                                    emojiInfo = evmEmojiInfo,
                                    parentEmoji = mainEmojiInfo,
                                    avatar = null,
                                    isActive = isSelectedWalletAddress(linkedWallet.address),
                                    type = RNBridge.AccountType.EVM,
                                    balance = null,
                                    nfts = null,
                                ))
                            }
                            is ChildWallet -> {
                                bridgeAccounts.add(RNBridge.WalletAccount(
                                    id = "child_${linkedWallet.address}",
                                    name = linkedWallet.name,
                                    address = linkedWallet.address,
                                    emojiInfo = null,
                                    parentEmoji = mainEmojiInfo,
                                    parentAddress = mainAddress,
                                    avatar = linkedWallet.icon,
                                    isActive = isSelectedWalletAddress(linkedWallet.address),
                                    type = RNBridge.AccountType.CHILD,
                                    balance = null,
                                    nfts = null,
                                ))
                            }
                        }
                    }
                }

                // 2. Process EOAWallets (already correctly populated by WalletDataManager based on canDeriveEoa)
                currentAccount.walletNodes.filterIsInstance<EOAWallet>().forEach { eoaWallet ->
                    val eoaAddress = eoaWallet.address
                    if (eoaAddress.isEmpty()) return@forEach

                    val eoaEmojiInfo = createEmojiInfo(eoaAddress)
                    bridgeAccounts.add(RNBridge.WalletAccount(
                        id = "eoa_${eoaAddress}",
                        name = eoaEmojiInfo?.name ?: "EOA Account",
                        address = eoaAddress,
                        parentAddress = null,
                        emojiInfo = eoaEmojiInfo,
                        parentEmoji = null,
                        avatar = null,
                        isActive = isSelectedWalletAddress(eoaAddress),
                        type = RNBridge.AccountType.EOA,
                        balance = null,
                        nfts = null,
                    ))
                }

                val response = RNBridge.WalletAccountsResponse(accounts = bridgeAccounts)
                uiScope { promise.resolve(bridgeModelToWritableMap(response)) }
            } catch (e: Exception) {
                val emptyResponse = RNBridge.WalletAccountsResponse(accounts = emptyList())
                uiScope { promise.resolve(bridgeModelToWritableMap(emptyResponse)) }
            }
        }
    }

    fun getSignKeyIndex(): Double {
        return try {
            val address = WalletManager.getCurrentFlowWalletAddress()

            val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()

            if (address.isNullOrBlank() || cryptoProvider == null) {
                return 0.0
            }

            // This is a synchronous method, but currentKeyId is suspend
            // We need to use a blocking call here since the interface expects a synchronous return
            val keyId = kotlinx.coroutines.runBlocking {
                FlowAddress(address).currentKeyId(cryptoProvider.getPublicKey())
            }

            // Return 0 if no valid key found (-1), otherwise return the key index
            if (keyId == -1) 0.0 else keyId.toDouble()
        } catch (e: Exception) {
            // Return 0 as default key index on any error
            logw(TAG, "getSignKeyIndex() error: ${e.message}")
            0.0
        }
    }

    fun getSelectedAccount(promise: Promise, bridgeModelToWritableMap: (Any) -> WritableMap) {
        logd(TAG, "getSelectedAccount() called")
        ioScope {
            try {
                logd(TAG, "getSelectedAccount() - getting selected address...")
                val selectedAddress = WalletManager.selectedWalletAddress()
                if (selectedAddress.isEmpty()) {
                    logw(TAG, "getSelectedAccount() - no selected address found")
                    uiScope {
                        promise.reject("NO_SELECTED_ACCOUNT", "No wallet address selected", null)
                    }
                    return@ioScope
                }
                logd(TAG, "getSelectedAccount() - selected address: $selectedAddress")

                // Determine account type based on address using utility methods
                val mainAddress = WalletManager.getCurrentFlowWalletAddress()

                val accountType = when {
                    EVMWalletManager.isEVMWalletAddress(selectedAddress) -> RNBridge.AccountType.EVM
                    WalletManager.isChildAccount(selectedAddress) -> RNBridge.AccountType.CHILD
                    EVMWalletManager.isEOAAddress(selectedAddress) -> RNBridge.AccountType.EOA
                    else -> RNBridge.AccountType.MAIN
                }

                val selectedEmojiInfo = createEmojiInfo(selectedAddress)
                val selectedAccount = RNBridge.WalletAccount(
                    id = "selected",
                    name = selectedEmojiInfo?.name ?: "Selected Account",
                    address = selectedAddress,
                    emojiInfo = selectedEmojiInfo,
                    parentEmoji = if (accountType != RNBridge.AccountType.MAIN) createEmojiInfo(mainAddress) else null,
                    parentAddress = if (accountType != RNBridge.AccountType.MAIN) mainAddress else null,
                    avatar = null,
                    isActive = true,
                    type = accountType,
                    balance = null,
                    nfts = null,
                )

                val result = bridgeModelToWritableMap(selectedAccount)
                logd(TAG, "getSelectedAccount() - account mapped successfully")
                uiScope {
                    promise.resolve(result)
                }
            } catch (e: Exception) {
                loge(TAG, "getSelectedAccount() - error: ${e.message}")
                e.printStackTrace()
                uiScope {
                    promise.reject("SELECTED_ACCOUNT_ERROR", "Failed to get selected account: ${e.message}", e)
                }
            }
        }
    }

    fun getWalletProfiles(promise: Promise, bridgeModelToWritableMap: (Any) -> WritableMap) {
        logd(TAG, "getWalletProfiles() called")
        ioScope {
            try {
                logd(TAG, "getWalletProfiles() - getting all accounts from AccountManager...")

                // Get all accounts from AccountManager
                val accounts = AccountManager.list()
                logd(TAG, "getWalletProfiles() - found ${accounts.size} accounts")

                val profiles = mutableListOf<RNBridge.WalletProfile>()

                // Create wallet profile for each account
                accounts.forEach { account ->
                    createWalletProfileFromAccount(account, bridgeModelToWritableMap)?.let { profile ->
                        profiles.add(profile)
                        logd(TAG, "getWalletProfiles() - added profile for account: ${account.userInfo.username}")
                    }
                }

                val response = RNBridge.WalletProfilesResponse(profiles = profiles)
                val result = bridgeModelToWritableMap(response)

                logd(TAG, "getWalletProfiles() - ${profiles.size} profiles mapped successfully")
                uiScope {
                    promise.resolve(result)
                }
            } catch (e: Exception) {
                loge(TAG, "getWalletProfiles() - error: ${e.message}")
                e.printStackTrace()

                // Return empty profiles on error to maintain consistency
                val emptyResponse = RNBridge.WalletProfilesResponse(profiles = emptyList())
                val result = bridgeModelToWritableMap(emptyResponse)
                uiScope {
                    promise.resolve(result)
                }
            }
        }
    }

    private fun createWalletProfileFromAccount(account: Account, bridgeModelToWritableMap: (Any) -> WritableMap): RNBridge.WalletProfile? {
        return try {
            logd(TAG, "createWalletProfileFromAccount() - creating profile for account: ${account.userInfo.username}")

            // Get user info from the specific account (similar to AccountManager.userInfo())
            val userInfo = account.userInfo
            logd(TAG, "createWalletProfileFromAccount() - userInfo: ${userInfo.username}, avatar: ${userInfo.avatar}")

            // Get user ID similar to the original implementation
            val userId = account.wallet?.id ?: ""
            logd(TAG, "createWalletProfileFromAccount() - userId: $userId")

            val bridgeAccounts = mutableListOf<RNBridge.WalletAccount>()

            // Get FlowWallet for current network from walletNodes
            val currentNetwork = com.flowfoundation.wallet.manager.app.chainNetWorkString()
            val flowWallets = account.walletNodes.filterIsInstance<FlowWallet>()
                .filter { it.chainIdString == currentNetwork }

            logd(TAG, "createWalletProfileFromAccount() - found ${flowWallets.size} FlowWallets for network $currentNetwork")

            if (flowWallets.isEmpty()) {
                logw(TAG, "createWalletProfileFromAccount() - no FlowWallet found for account: ${account.userInfo.username} on network $currentNetwork")
                return null
            }

            // Process each FlowWallet (typically one per network)
            flowWallets.forEach { flowWallet ->
                val mainAddress = flowWallet.address
                val mainEmojiInfo = createEmojiInfo(mainAddress)

                // Add main account
                val mainAccount = RNBridge.WalletAccount(
                    id = "main_${mainAddress}",
                    name = mainEmojiInfo?.name ?: flowWallet.name,
                    address = mainAddress,
                    emojiInfo = mainEmojiInfo,
                    parentEmoji = null,
                    parentAddress = null,
                    avatar = null,
                    isActive = isSelectedWalletAddress(mainAddress),
                    type = RNBridge.AccountType.MAIN,
                    balance = null,
                    nfts = null,
                )
                bridgeAccounts.add(mainAccount)

                // Process linked wallets from walletNodes (COAWallet, ChildWallet)
                flowWallet.linkedWallets.forEach { linkedWallet ->
                    when (linkedWallet) {
                        is COAWallet -> {
                            val evmEmojiInfo = createEmojiInfo(linkedWallet.address)
                            val evmAccount = RNBridge.WalletAccount(
                                id = "evm_${linkedWallet.address}",
                                name = evmEmojiInfo?.name ?: linkedWallet.name,
                                address = linkedWallet.address,
                                parentAddress = mainAddress,
                                emojiInfo = evmEmojiInfo,
                                parentEmoji = mainEmojiInfo,
                                avatar = null,
                                isActive = isSelectedWalletAddress(linkedWallet.address),
                                type = RNBridge.AccountType.EVM,
                                balance = null,
                                nfts = null,
                            )
                            bridgeAccounts.add(evmAccount)
                            logd(TAG, "createWalletProfileFromAccount() - added COA/EVM: ${linkedWallet.address}")
                        }
                        is ChildWallet -> {
                            val childAccountBridge = RNBridge.WalletAccount(
                                id = "child_${linkedWallet.address}",
                                name = linkedWallet.name,
                                address = linkedWallet.address,
                                emojiInfo = null,
                                parentEmoji = mainEmojiInfo,
                                parentAddress = mainAddress,
                                avatar = linkedWallet.icon,
                                isActive = isSelectedWalletAddress(linkedWallet.address),
                                type = RNBridge.AccountType.CHILD,
                                balance = null,
                                nfts = null,
                            )
                            bridgeAccounts.add(childAccountBridge)
                            logd(TAG, "createWalletProfileFromAccount() - added Child: ${linkedWallet.address}")
                        }
                    }
                }
            }

            // Add EOA addresses from account's walletNodes
            // EOAWallet is only present when canDeriveEoa=true (populated by WalletDataManager)
            try {
                val eoaWallets = account.walletNodes.filterIsInstance<EOAWallet>()

                for (eoaWallet in eoaWallets) {
                    val eoaAddress = eoaWallet.address
                    if (eoaAddress.isEmpty()) continue

                    val eoaEmojiInfo = createEmojiInfo(eoaAddress)
                    val eoaAccount = RNBridge.WalletAccount(
                        id = "eoa_${eoaAddress}",
                        name = eoaEmojiInfo?.name ?: "EVM Account (EOA)",
                        address = eoaAddress,
                        parentAddress = null,
                        emojiInfo = eoaEmojiInfo,
                        parentEmoji = null,
                        avatar = null,
                        isActive = isSelectedWalletAddress(eoaAddress),
                        type = RNBridge.AccountType.EOA,
                        balance = null,
                        nfts = null,
                    )
                    bridgeAccounts.add(eoaAccount)
                }
            } catch (e: Exception) {
                logw(TAG, "createWalletProfileFromAccount() - EOA accounts not available: ${e.message}")
            }

            // Create wallet profile
            RNBridge.WalletProfile(
                name = account.userInfo.nickname,
                avatar = account.userInfo.avatar,
                uid = userId,
                accounts = bridgeAccounts
            )
        } catch (e: Exception) {
            loge(TAG, "createWalletProfileFromAccount() - error creating profile for account: ${account.userInfo.username}, error: ${e.message}")
            null
        }
    }

    /**
     * Get profiles that are stored locally but not yet logged in (for recovery flow)
     * These are LocalSwitchAccount entries - accounts known to the device but not fully authenticated
     */
    fun getRecoverableProfiles(promise: Promise, bridgeModelToWritableMap: (Any) -> WritableMap) {
        logd(TAG, "getRecoverableProfiles() called")
        ioScope {
            try {
                // Get the switch account list which includes LocalSwitchAccount entries
                val switchList = AccountManager.getSwitchAccountList()
                logd(TAG, "getRecoverableProfiles() - found ${switchList.size} items in switch list")

                val profiles = mutableListOf<RNBridge.WalletProfile>()

                // Filter for LocalSwitchAccount entries (profiles stored locally but not logged in)
                switchList.filterIsInstance<com.flowfoundation.wallet.manager.account.model.LocalSwitchAccount>().forEach { localAccount ->
                    logd(TAG, "getRecoverableProfiles() - processing LocalSwitchAccount: ${localAccount.username}")

                    val displayAddress = localAccount.address.ifBlank { null }
                    val mainEmojiInfo = if (displayAddress != null) createEmojiInfo(displayAddress) else null
                    val mainAccount = RNBridge.WalletAccount(
                        id = "main_${displayAddress ?: localAccount.userId ?: localAccount.username}",
                        name = mainEmojiInfo?.name ?: localAccount.username,
                        address = displayAddress ?: "",
                        emojiInfo = mainEmojiInfo,
                        parentEmoji = null,
                        parentAddress = null,
                        avatar = null,
                        isActive = false,
                        type = RNBridge.AccountType.MAIN,
                        balance = null,
                        nfts = null,
                    )

                    val profile = RNBridge.WalletProfile(
                        name = localAccount.username,
                        avatar = null,
                        uid = localAccount.userId ?: localAccount.address,
                        accounts = listOf(mainAccount)
                    )
                    profiles.add(profile)
                }

                val response = RNBridge.WalletProfilesResponse(profiles = profiles)
                val result = bridgeModelToWritableMap(response)

                logd(TAG, "getRecoverableProfiles() - ${profiles.size} recoverable profiles found")
                uiScope {
                    promise.resolve(result)
                }
            } catch (e: Exception) {
                loge(TAG, "getRecoverableProfiles() - error: ${e.message}")
                e.printStackTrace()

                val emptyResponse = RNBridge.WalletProfilesResponse(profiles = emptyList())
                val result = bridgeModelToWritableMap(emptyResponse)
                uiScope {
                    promise.resolve(result)
                }
            }
        }
    }

    fun switchToProfile(userId: String, promise: Promise) {
        logd(TAG, "switchToProfile() called with userId: $userId")
        ioScope {
            try {
                // 1. Check normal logged-in accounts first
                val targetAccount = AccountManager.list().find { it.wallet?.id == userId }
                if (targetAccount != null) {
                    logd(TAG, "switchToProfile() - found normal account: ${targetAccount.userInfo.username}")
                    AccountManager.switch(targetAccount) {
                        logd(TAG, "switchToProfile() - switch completed for userId: $userId")
                        uiScope { promise.resolve(null) }
                    }
                    return@ioScope
                }

                // 2. Not a normal account — check LocalSwitchAccount list (orphan keys)
                // These are accounts with key material but no account cache entry.
                val localAccount = AccountManager.getSwitchAccountList()
                    .filterIsInstance<com.flowfoundation.wallet.manager.account.model.LocalSwitchAccount>()
                    .find { it.userId == userId }

                if (localAccount != null) {
                    logd(TAG, "switchToProfile() - found LocalSwitchAccount for userId: $userId")
                    AccountManager.switch(localAccount) {
                        logd(TAG, "switchToProfile() - LocalSwitchAccount switch completed for userId: $userId")
                        uiScope { promise.resolve(null) }
                    }
                    return@ioScope
                }

                logw(TAG, "switchToProfile() - account not found for userId: $userId")
                uiScope {
                    promise.reject("PROFILE_NOT_FOUND", "Account not found for userId: $userId")
                }
            } catch (e: Exception) {
                loge(TAG, "switchToProfile() - error: ${e.message}")
                e.printStackTrace()
                uiScope {
                    promise.reject("SWITCH_FAILED", "Failed to switch profile: ${e.message}", e)
                }
            }
        }
    }
}
