package com.flowfoundation.wallet.reactnative.bridge.handlers

import android.widget.Toast
import com.flowfoundation.wallet.reactnative.bridge.RNBridge
import com.flowfoundation.wallet.reactnative.bridge.createEmojiInfo
import com.flowfoundation.wallet.reactnative.bridge.isSelectedWalletAddress
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import com.flow.wallet.CryptoProvider
import com.flowfoundation.wallet.cache.recentTransactionCache
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.currentKeyId
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.wallet.walletAddress
import com.flowfoundation.wallet.utils.getWatchCollectibleAddress
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.utils.logw
import com.flowfoundation.wallet.wallet.Wallet
import com.flowfoundation.wallet.manager.evm.EVMWalletManager.isValidEVMAddress
import com.flowfoundation.wallet.manager.evm.EVMWalletManager.toChecksumEVMAddress
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

                // Get main wallet address - for hardware-backed keys, wallet() returns null,
                // so we need to use selectedWalletAddress() as fallback
                var mainAddress = WalletManager.wallet()?.walletAddress()
                if (mainAddress.isNullOrEmpty()) {
                    // Hardware-backed key fallback: use the selected address
                    mainAddress = WalletManager.selectedWalletAddress()
                }
                val mainEmojiInfo = createEmojiInfo(mainAddress)
                if (mainAddress.isNotEmpty()) {
                    // For secure enclave COA accounts, check if main address has EVM capabilities
                    // If it does, set type to EVM instead of MAIN
                    // Check both evmAddressMap and getEVMAddress() since evmAddressMap might not be populated yet
                    val mainAccountType = try {
                        val isEVM = EVMWalletManager.isEVMWalletAddress(mainAddress) ||
                            mainAddress.equals(EVMWalletManager.getEVMAddress(), ignoreCase = true) ||
                            EVMWalletManager.isValidEVMAddress(mainAddress)
                        if (isEVM) {
                            RNBridge.AccountType.EVM
                        } else {
                            RNBridge.AccountType.MAIN
                        }
                    } catch (e: Exception) {
                        RNBridge.AccountType.MAIN
                    }

                    val mainAccount = RNBridge.WalletAccount(
                        id = "main",
                        name = mainEmojiInfo?.name ?: "Main Account",
                        address = mainAddress,
                        emojiInfo = mainEmojiInfo,
                        parentEmoji = null,
                        parentAddress = null,
                        avatar = null,
                        isActive = isSelectedWalletAddress(mainAddress),
                        type = mainAccountType,
                        balance = null,
                        nfts = null,
                    )
                    bridgeAccounts.add(mainAccount)
                }

                // Get child accounts
                try {
                    val childAccounts = WalletManager.childAccountList(mainAddress)?.get()
                    childAccounts?.forEach { childAccount ->
                        // Debug: Log child account data to see if icon is available
                        println("DEBUG: Child account - name: ${childAccount.name}, icon: ${childAccount.icon}, address: ${childAccount.address}")

                        // For secure enclave COA accounts, check if the address has EVM capabilities
                        // If it does, set type to EVM instead of CHILD
                        // Check both evmAddressMap and getEVMAddress() since evmAddressMap might not be populated yet
                        // For hardware-backed keys (secure enclave), WalletManager.wallet() returns null
                        val childAccountType = try {
                            val isSecureEnclave = WalletManager.wallet() == null

                            // For secure enclave accounts (hardware-backed keys), child accounts are COA accounts which are EVM
                            val isEVM = if (isSecureEnclave) {
                                // Secure enclave: COA accounts are EVM accounts
                                true
                            } else {
                                // EOA accounts: check if address has EVM capabilities
                                EVMWalletManager.isEVMWalletAddress(childAccount.address) ||
                                childAccount.address.equals(EVMWalletManager.getEVMAddress(), ignoreCase = true) ||
                                EVMWalletManager.isValidEVMAddress(childAccount.address)
                            }

                            if (isEVM) {
                                RNBridge.AccountType.EVM
                            } else {
                                RNBridge.AccountType.CHILD
                            }
                        } catch (e: Exception) {
                            RNBridge.AccountType.CHILD
                        }

                        val childAccountBridge = RNBridge.WalletAccount(
                            id = "child_${childAccount.address}",
                            name = childAccount.name,
                            address = childAccount.address,
                            emojiInfo = null,
                            parentEmoji = mainEmojiInfo,
                            parentAddress = mainAddress,
                            avatar = childAccount.icon, // Include the squid avatar!
                            isActive = isSelectedWalletAddress(childAccount.address),
                            type = childAccountType,
                            balance = null,
                            nfts = null,
                        )
                        bridgeAccounts.add(childAccountBridge)
                    }
                } catch (e: Exception) {
                    // Child accounts might not be available, continue without them
                    println("Child accounts not available: ${e.message}")
                }

                // Get EVM address if available
                // For secure enclave (hardware-backed keys), skip adding separate EVM account entry
                // because the COA child account already represents the EVM account
                var evmAddress: String? = null
                val isSecureEnclave = WalletManager.wallet() == null

                try {
                    evmAddress = EVMWalletManager.getEVMAddress()
                    if (!evmAddress.isNullOrEmpty()) {
                        // Check if EVM address matches any child account address
                        // If it does, don't add a separate EVM account entry (it's already represented as a child account)
                        val childAccounts = WalletManager.childAccountList(mainAddress)?.get()
                        val evmMatchesChildAccount = childAccounts?.any {
                            it.address.equals(evmAddress, ignoreCase = true)
                        } ?: false

                        // Only add EVM account entry if:
                        // 1. Not secure enclave (EOA flow), OR
                        // 2. EVM address doesn't match any child account (shouldn't happen, but safety check)
                        if (!isSecureEnclave || !evmMatchesChildAccount) {
                            val evmEmojiInfo = createEmojiInfo(evmAddress)

                            val evmAccount = RNBridge.WalletAccount(
                                id = "evm",
                                name = evmEmojiInfo?.name ?: "EVM Account",
                                address = evmAddress,
                                parentAddress = mainAddress,
                                emojiInfo = evmEmojiInfo,
                                parentEmoji = mainEmojiInfo,
                                avatar = null,
                                isActive = isSelectedWalletAddress(evmAddress),
                                type = RNBridge.AccountType.EVM,
                                balance = null,
                                nfts = null,
                            )
                            bridgeAccounts.add(evmAccount)
                        }
                    }
                } catch (e: Exception) {
                    // EVM account might not be available, continue without it
                    println("EVM account not available: ${e.message}")
                }

                // Get EOA address only if it's different from EVM address
                // For secure enclave (COA) accounts, EOA and EVM addresses are the same,
                // so we should only show the EVM account to avoid duplicate "EOA" chip
                try {
                    val eoaAddress = WalletManager.getEOAAddressCached()
                    if (!eoaAddress.isNullOrEmpty()) {
                        // Only add EOA account if it's different from EVM address
                        // Check if account is Secure Enclave by checking if it has a prefix
                        // Recovery Phrase accounts have a prefix, Secure Enclave accounts don't
                        val currentAccount = com.flowfoundation.wallet.manager.account.AccountManager.get()
                        val isSecureEnclaveAccount = currentAccount?.prefix.isNullOrBlank() && 
                            currentAccount?.keyStoreInfo.isNullOrBlank()
                        
                        // Only add EOA if:
                        // 1. EOA address is different from EVM address, AND
                        // 2. Account is NOT Secure Enclave (has prefix or keystore info)
                        val isDifferentFromEVM = evmAddress == null ||
                            !eoaAddress.equals(evmAddress, ignoreCase = true)

                        if (isDifferentFromEVM && !isSecureEnclaveAccount) {
                            val eoaEmojiInfo = createEmojiInfo(eoaAddress)
                            val eoaAccount = RNBridge.WalletAccount(
                                id = "eoa",
                                name = eoaEmojiInfo?.name ?: "EOA Account",
                                address = eoaAddress,
                                parentAddress = mainAddress,
                                emojiInfo = eoaEmojiInfo,
                                parentEmoji = mainEmojiInfo,
                                avatar = null,
                                isActive = isSelectedWalletAddress(eoaAddress),
                                type = RNBridge.AccountType.EVM,
                                balance = null,
                                nfts = null,
                            )
                            bridgeAccounts.add(eoaAccount)
                        }
                    }
                } catch (e: Exception) {
                    // EOA account might not be available, continue without it
                    println("EOA account not available: ${e.message}")
                }

                val response = RNBridge.WalletAccountsResponse(accounts = bridgeAccounts)
                val result = bridgeModelToWritableMap(response)

                uiScope {
                    promise.resolve(result)
                }
            } catch (e: Exception) {
                val emptyResponse = RNBridge.WalletAccountsResponse(accounts = emptyList())
                val result = bridgeModelToWritableMap(emptyResponse)
                uiScope {
                    promise.resolve(result)
                }
            }
        }
    }

    fun getSignKeyIndex(): Double {
        return try {
            val address = WalletManager.selectedWalletAddress()
            if (address.isEmpty()) {
                logw(TAG, "getSignKeyIndex() - no selected address")
                return 0.0
            }

            val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()
            if (cryptoProvider == null) {
                logw(TAG, "getSignKeyIndex() - no crypto provider")
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
                val mainAddress = WalletManager.wallet()?.walletAddress()

                val accountType = when {
                    EVMWalletManager.isEOAAddress(selectedAddress) || EVMWalletManager.isEVMWalletAddress(selectedAddress) -> RNBridge.AccountType.EVM
                    // For child accounts, check if they have EVM capabilities (e.g., secure enclave COA accounts)
                    // Check both evmAddressMap and getEVMAddress() since evmAddressMap might not be populated yet
                    // For hardware-backed keys (secure enclave), WalletManager.wallet() returns null
                    WalletManager.isChildAccount(selectedAddress) -> {
                        val isSecureEnclave = WalletManager.wallet() == null

                        val isEVM = if (isSecureEnclave) {
                            // Secure enclave: COA accounts are EVM accounts
                            true
                        } else {
                            // EOA accounts: check if address has EVM capabilities
                            try {
                                EVMWalletManager.isEVMWalletAddress(selectedAddress) ||
                                selectedAddress.equals(EVMWalletManager.getEVMAddress(), ignoreCase = true) ||
                                EVMWalletManager.isValidEVMAddress(selectedAddress)
                            } catch (e: Exception) {
                                false
                            }
                        }

                        if (isEVM) {
                            RNBridge.AccountType.EVM
                        } else {
                            RNBridge.AccountType.CHILD
                        }
                    }
                    // For secure enclave COA accounts, the main address might be the EVM address
                    // Check if selected address is the main address and has EVM capabilities
                    selectedAddress.equals(mainAddress, ignoreCase = true) && EVMWalletManager.isEVMWalletAddress(selectedAddress) -> RNBridge.AccountType.EVM
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

            // Get main wallet address from account
            val mainAddress = account.wallet?.walletAddress()
            if (mainAddress.isNullOrEmpty()) {
                logw(TAG, "createWalletProfileFromAccount() - no main address found for account: ${account.userInfo.username}")
                return null
            }

            val mainEmojiInfo = createEmojiInfo(mainAddress)
            val mainAccount = RNBridge.WalletAccount(
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
            )
            bridgeAccounts.add(mainAccount)

            // Get child accounts
            try {
                val childAccounts = WalletManager.childAccountList(mainAddress)?.get()
                childAccounts?.forEach { childAccount ->
                    // For secure enclave COA accounts, check if the address has EVM capabilities
                    // If it does, set type to EVM instead of CHILD
                    // Check both evmAddressMap and getEVMAddress() since evmAddressMap might not be populated yet
                    // For hardware-backed keys (secure enclave), WalletManager.wallet() returns null
                    val childAccountType = try {
                        val isSecureEnclave = WalletManager.wallet() == null

                        // For secure enclave accounts (hardware-backed keys), child accounts are COA accounts which are EVM
                        val isEVM = if (isSecureEnclave) {
                            // Secure enclave: COA accounts are EVM accounts
                            true
                        } else {
                            // EOA accounts: check if address has EVM capabilities
                            EVMWalletManager.isEVMWalletAddress(childAccount.address) ||
                            childAccount.address.equals(EVMWalletManager.getEVMAddress(), ignoreCase = true) ||
                            EVMWalletManager.isValidEVMAddress(childAccount.address)
                        }

                        if (isEVM) {
                            RNBridge.AccountType.EVM
                        } else {
                            RNBridge.AccountType.CHILD
                        }
                    } catch (e: Exception) {
                        RNBridge.AccountType.CHILD
                    }

                    val childAccountBridge = RNBridge.WalletAccount(
                        id = "child_${childAccount.address}",
                        name = childAccount.name,
                        address = childAccount.address,
                        emojiInfo = null,
                        parentEmoji = mainEmojiInfo,
                        parentAddress = mainAddress,
                        avatar = childAccount.icon,
                        isActive = isSelectedWalletAddress(childAccount.address),
                        type = childAccountType,
                        balance = null,
                        nfts = null,
                    )
                    bridgeAccounts.add(childAccountBridge)
                }
            } catch (e: Exception) {
                logw(TAG, "createWalletProfileFromAccount() - child accounts not available: ${e.message}")
            }

            // Get EVM address if available
            // For secure enclave (hardware-backed keys), skip adding separate EVM account entry
            // because the COA child account already represents the EVM account
            var evmAddress: String? = null
            val isSecureEnclave = WalletManager.wallet() == null

            try {
                evmAddress = if (isSelectedWalletAddress(mainAddress)) {
                    EVMWalletManager.getEVMAddress()
                } else {
                    val address = account.evmAddressData?.evmAddressMap?.get(mainAddress)
                    if (address.isNullOrBlank() || address == "0x") {
                        null
                    } else {
                      val checksumAddress = toChecksumEVMAddress(address)
                      // Validate the address format - if it's corrupted, try to refresh it
                      if (!isValidEVMAddress(checksumAddress)) {
                        logd(TAG, "Detected corrupted EVM address: $checksumAddress, attempting to refresh")
                        return null
                      }
                      checksumAddress
                    }
                }
                if (!evmAddress.isNullOrEmpty()) {
                    // Check if EVM address matches any child account address
                    // If it does, don't add a separate EVM account entry (it's already represented as a child account)
                    val childAccounts = WalletManager.childAccountList(mainAddress)?.get()
                    val evmMatchesChildAccount = childAccounts?.any {
                        it.address.equals(evmAddress, ignoreCase = true)
                    } ?: false

                    // Only add EVM account entry if:
                    // 1. Not secure enclave (EOA flow), OR
                    // 2. EVM address doesn't match any child account (shouldn't happen, but safety check)
                    if (!isSecureEnclave || !evmMatchesChildAccount) {
                        val evmEmojiInfo = createEmojiInfo(evmAddress)
                        val evmAccount = RNBridge.WalletAccount(
                            id = "evm",
                            name = evmEmojiInfo?.name ?: "EVM Account",
                            address = evmAddress,
                            parentAddress = mainAddress,
                            emojiInfo = evmEmojiInfo,
                            parentEmoji = mainEmojiInfo,
                            avatar = null,
                            isActive = isSelectedWalletAddress(evmAddress),
                            type = RNBridge.AccountType.EVM,
                            balance = null,
                            nfts = null,
                        )
                        bridgeAccounts.add(evmAccount)
                    }
                }
            } catch (e: Exception) {
                logw(TAG, "createWalletProfileFromAccount() - EVM account not available: ${e.message}")
            }

            // Get EOA address only if it's different from EVM address
            // For secure enclave (COA) accounts, EOA and EVM addresses are the same,
            // so we should only show the EVM account to avoid duplicate "EOA" chip
            try {
                val eoaAddress = if (isSelectedWalletAddress(mainAddress)) {
                    WalletManager.getEOAAddressCached()
                } else {
                    ""
                }
              if (!eoaAddress.isNullOrEmpty()) {
                  // Only add EOA account if it's different from EVM address
                  // and if wallet has a mnemonic (not secure enclave)
                  val isDifferentFromEVM = evmAddress == null ||
                      !eoaAddress.equals(evmAddress, ignoreCase = true)
                  val hasMnemonic = try {
                      Wallet.store().mnemonic().isNotEmpty()
                  } catch (e: Exception) {
                      false // No mnemonic (secure enclave)
                  }

                  if (isDifferentFromEVM && hasMnemonic) {
                      val eoaEmojiInfo = createEmojiInfo(eoaAddress)
                      val eoaAccount = RNBridge.WalletAccount(
                        id = "eoa",
                        name = eoaEmojiInfo?.name ?: "EOA Account",
                        address = eoaAddress,
                        parentAddress = mainAddress,
                        emojiInfo = eoaEmojiInfo,
                        parentEmoji = mainEmojiInfo,
                        avatar = null,
                        isActive = isSelectedWalletAddress(eoaAddress),
                        type = RNBridge.AccountType.EVM,
                        balance = null,
                        nfts = null,
                      )
                      bridgeAccounts.add(eoaAccount)
                  }
                }
            } catch (e: Exception) {
                logw(TAG, "createWalletProfileFromAccount() - EOA account not available: ${e.message}")
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
}
