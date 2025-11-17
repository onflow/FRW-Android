package com.flowfoundation.wallet.reactnative.bridge.handlers

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.WritableNativeMap
import com.flowfoundation.wallet.firebase.auth.getFirebaseJwt
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.getFlowAddress
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flow.wallet.CryptoProvider
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.network.model.WalletListData
import com.flowfoundation.wallet.reactnative.bridge.RNBridge
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logw
import com.flowfoundation.wallet.utils.uiScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.flow.wallet.crypto.BIP39
import org.onflow.flow.models.hexToBytes
import org.onflow.flow.models.toHexString

/**
 * Handler for authentication and account creation bridge methods
 * Handles: JWT, account registration, COA creation, seed phrase generation, Firebase auth
 */
class AuthBridgeHandler(private val reactContext: ReactApplicationContext) {

    private val TAG = "AuthBridgeHandler"

    fun getJWT(promise: Promise) {
        logd(TAG, "getJWT() called")
        ioScope {
            try {
                logd(TAG, "getJWT() - getting Firebase JWT...")
                val jwt = getFirebaseJwt()

                logd(TAG, "getJWT() - JWT obtained successfully (length: ${jwt.length})")
                uiScope {
                    promise.resolve(jwt)
                }
            } catch (e: Exception) {
                loge(TAG, "getJWT() - error: ${e.message}")
                uiScope {
                    promise.reject("JWT_ERROR", "Failed to get Firebase JWT: ${e.message}", e)
                }
            }
        }
    }

    fun registerSecureTypeAccount(username: String, promise: Promise) {
        logd(TAG, "registerSecureTypeAccount() called - Registering Secure Type Account (Secure Enclave)")
        logd(TAG, "registerSecureTypeAccount() - username: $username")
        ioScope {
            try {
                logd(TAG, "registerSecureTypeAccount() - starting account registration...")

                // Use the existing registerOutblock function which creates a Secure Type/COA account:
                // - Generates keys (secure enclave on supported devices)
                // - Registers with backend server
                // - Creates Flow blockchain account
                // - Sets up Firebase authentication
                // - Creates local Account in AccountManager
                val success = com.flowfoundation.wallet.network.registerOutblock(username)

                if (success) {
                    logd(TAG, "registerSecureTypeAccount() - account created successfully")

                    // Get the created account details
                    val account = AccountManager.get()
                    val address = WalletManager.selectedWalletAddress()

                    // Note: Secure Type accounts use hardware-backed keys (Secure Enclave)
                    // No mnemonic is generated or stored for these accounts

                    // Create success response using WritableMap
                    val response = WritableNativeMap()
                    response.putBoolean("success", true)
                    response.putString("address", address)
                    response.putString("username", account?.userInfo?.username ?: username)
                    response.putString("accountType", "coa")
                    response.putNull("error")

                    uiScope {
                        promise.resolve(response)
                    }
                } else {
                    loge(TAG, "registerSecureTypeAccount() - account creation failed (registerOutblock returned false)")
                    loge(TAG, "registerSecureTypeAccount() - Check UserRegisterUtils logs for detailed error information")

                    val response = WritableNativeMap()
                    response.putBoolean("success", false)
                    response.putNull("address")
                    response.putNull("username")
                    response.putString("accountType", "coa")
                    response.putString("error", "Failed to register secure type account. Check logs for details.")

                    uiScope {
                        promise.resolve(response)
                    }
                }
            } catch (e: Exception) {
                loge(TAG, "registerSecureTypeAccount() - error: ${e.message}")
                e.printStackTrace()

                val response = WritableNativeMap()
                response.putBoolean("success", false)
                response.putNull("address")
                response.putNull("username")
                response.putString("accountType", "coa")
                response.putString("error", e.message ?: "Unknown error")

                uiScope {
                    promise.resolve(response)
                }
            }
        }
    }

    fun createLinkedCOAAccount(promise: Promise) {
        logd(TAG, "createLinkedCOAAccount() called - Creating linked COA account for Recovery Phrase flow")
        ioScope {
            try {
                // Ensure WalletManager is initialized and wallet is ready
                // Wait for wallet to be available (with retries)
                var retries = 0
                val maxRetries = 10
                val retryDelayMs = 200L
                
                while (retries < maxRetries) {
                    WalletManager.init() // Ensure initialization
                    val wallet = WalletManager.wallet()
                    val selectedAddress = WalletManager.selectedWalletAddress()
                    
                    if (wallet != null || !selectedAddress.isNullOrBlank()) {
                        logd(TAG, "createLinkedCOAAccount() - Wallet is ready (attempt ${retries + 1})")
                        break
                    }
                    
                    retries++
                    if (retries < maxRetries) {
                        logd(TAG, "createLinkedCOAAccount() - Wallet not ready yet, waiting... (attempt $retries/$maxRetries)")
                        kotlinx.coroutines.delay(retryDelayMs)
                    }
                }
                
                // Verify wallet is ready
                val finalWallet = WalletManager.wallet()
                val finalAddress = WalletManager.selectedWalletAddress()
                
                if (finalWallet == null && finalAddress.isNullOrBlank()) {
                    loge(TAG, "createLinkedCOAAccount() - Wallet not ready after $maxRetries attempts")
                    uiScope {
                        promise.reject("COA_CREATION_ERROR", "Wallet not ready: cannot create COA account. Wallet: ${finalWallet != null}, Address: ${finalAddress.isNullOrBlank()}")
                    }
                    return@ioScope
                }
                
                logd(TAG, "createLinkedCOAAccount() - Wallet ready, creating COA account...")
                logd(TAG, "createLinkedCOAAccount() - Final wallet: ${finalWallet != null}, Final address: $finalAddress")
                
                // Wait for the exact account created by saveMnemonic to have its Flow address populated
                // After saveMnemonic, the account is set as current via AccountManager
                // We must use AccountManager.get() to get the exact account that was just created
                // and wait for its Flow address to be populated from Account.wallet data
                var accountRetries = 0
                val maxAccountRetries = 30 // Increased retries to allow more time for transaction finalization
                val accountRetryDelayMs = 500L // Increased delay to allow transaction to finalize
                val currentNetwork = com.flowfoundation.wallet.manager.app.chainNetWorkString()
                
                var finalAccount: com.flowfoundation.wallet.manager.account.Account? = null
                var finalAccountAddress: String? = null
                
                while (accountRetries < maxAccountRetries) {
                    // Get the exact current account - this is the account that was just created by saveMnemonic
                    val currentAccount = com.flowfoundation.wallet.manager.account.AccountManager.get()
                    
                    if (currentAccount == null) {
                        logd(TAG, "createLinkedCOAAccount() - Current account not available yet (attempt ${accountRetries + 1})")
                        accountRetries++
                        if (accountRetries < maxAccountRetries) {
                            kotlinx.coroutines.delay(accountRetryDelayMs)
                        }
                        continue
                    }
                    
                    // Try to get Flow address from multiple sources:
                    // 1. From Account.wallet data (backend WalletListData) - most reliable but may not be synced yet
                    // 2. From WalletManager.wallet().accounts (wallet SDK discovered accounts) - available after transaction finalizes
                    var flowAddress: String? = null
                    
                    // First, try account's wallet data (backend)
                    flowAddress = currentAccount.getFlowAddress(currentNetwork, TAG)
                    
                    // If not available from backend, try wallet SDK's discovered accounts
                    if (flowAddress.isNullOrBlank()) {
                        val wallet = WalletManager.wallet()
                        if (wallet != null) {
                            val chainId = when (currentNetwork.lowercase()) {
                                "mainnet" -> org.onflow.flow.ChainId.Mainnet
                                "testnet" -> org.onflow.flow.ChainId.Testnet
                                else -> null
                            }
                            if (chainId != null) {
                                val walletAccounts = wallet.accounts[chainId]
                                walletAccounts?.firstOrNull()?.let { flowAccount ->
                                    val discoveredAddress = flowAccount.address?.toString()
                                    if (!discoveredAddress.isNullOrBlank()) {
                                        flowAddress = discoveredAddress
                                        logd(TAG, "createLinkedCOAAccount() - Found Flow address from wallet SDK discovered accounts: $flowAddress")
                                    }
                                }
                            }
                        }
                    }
                    
                    if (flowAddress != null && flowAddress.isNotBlank()) {
                        // Found the Flow address for the exact account
                        finalAccount = currentAccount
                        finalAccountAddress = flowAddress
                        logd(TAG, "createLinkedCOAAccount() - Found Flow address for current account (attempt ${accountRetries + 1})")
                        logd(TAG, "createLinkedCOAAccount() - Account username: ${currentAccount.userInfo.username}, prefix: ${currentAccount.prefix}, address: $flowAddress")
                        break
                    } else {
                        // Account exists but Flow address not populated yet - continue waiting
                        logd(TAG, "createLinkedCOAAccount() - Current account exists (${currentAccount.userInfo.username}) but Flow address not available yet (attempt ${accountRetries + 1})")
                        logd(TAG, "createLinkedCOAAccount() - Waiting for Flow address to be discovered...")
                        val wallet = WalletManager.wallet()
                        if (wallet != null) {
                            val chainId = when (currentNetwork.lowercase()) {
                                "mainnet" -> org.onflow.flow.ChainId.Mainnet
                                "testnet" -> org.onflow.flow.ChainId.Testnet
                                else -> null
                            }
                            val walletAccounts = chainId?.let { wallet.accounts[it] }
                            logd(TAG, "createLinkedCOAAccount() - Wallet accounts for $currentNetwork: ${walletAccounts?.size ?: 0}")
                        } else {
                            logd(TAG, "createLinkedCOAAccount() - WalletManager.wallet() is null")
                        }
                    }
                    
                    accountRetries++
                    if (accountRetries < maxAccountRetries) {
                        kotlinx.coroutines.delay(accountRetryDelayMs)
                    }
                }
                
                if (finalAccount == null || finalAccountAddress == null) {
                    val currentAccount = com.flowfoundation.wallet.manager.account.AccountManager.get()
                    loge(TAG, "createLinkedCOAAccount() - Flow address not found for current account after $maxAccountRetries attempts")
                    if (currentAccount != null) {
                        val flowAddress = currentAccount.getFlowAddress(currentNetwork, TAG)
                        loge(TAG, "createLinkedCOAAccount() - Current account: username=${currentAccount.userInfo.username}, prefix=${currentAccount.prefix}, address=$flowAddress")
                        loge(TAG, "createLinkedCOAAccount() - Account.wallet is null: ${currentAccount.wallet == null}")
                    } else {
                        loge(TAG, "createLinkedCOAAccount() - Current account is null")
                    }
                    uiScope {
                        promise.reject("COA_CREATION_ERROR", "Account Flow address not available: cannot create COA account. Current account Flow address not populated.")
                    }
                    return@ioScope
                }
                
                logd(TAG, "createLinkedCOAAccount() - Account verified with Flow address: $finalAccountAddress, proceeding with COA creation...")
                
                // Check if COA account already exists before creating one
                // This prevents errors if COA account was already created (e.g., in Secure Enclave flow)
                val coaAlreadyExists = try {
                    com.flowfoundation.wallet.manager.flowjvm.cadenceCheckCOALink(finalAccountAddress)
                } catch (e: Exception) {
                    logw(TAG, "createLinkedCOAAccount() - Could not check COA link status: ${e.message}")
                    null // If check fails, proceed with creation attempt
                }
                
                if (coaAlreadyExists == true) {
                    logd(TAG, "createLinkedCOAAccount() - COA account already exists for address $finalAccountAddress, skipping creation")
                    uiScope {
                        // Return a success response indicating COA already exists
                        // This allows the flow to continue without error
                        promise.resolve("COA_ALREADY_EXISTS")
                    }
                    return@ioScope
                }
                
                // Execute Cadence transaction to create linked COA account
                val txId = try {
                    com.flowfoundation.wallet.manager.flowjvm.cadenceCreateCOAAccount()
                } catch (e: Exception) {
                    // Check if error is due to COA already existing
                    val errorMessage = e.message?.lowercase() ?: ""
                    if (errorMessage.contains("already") || errorMessage.contains("exists") || errorMessage.contains("duplicate")) {
                        logd(TAG, "createLinkedCOAAccount() - COA account already exists (detected from error), skipping creation")
                        uiScope {
                            promise.resolve("COA_ALREADY_EXISTS")
                        }
                        return@ioScope
                    }
                    loge(TAG, "createLinkedCOAAccount() - Exception calling cadenceCreateCOAAccount: ${e.message}")
                    e.printStackTrace()
                    null
                }

                if (txId.isNullOrBlank()) {
                    loge(TAG, "createLinkedCOAAccount() - Transaction ID is null or empty")
                    loge(TAG, "createLinkedCOAAccount() - Wallet state: wallet=${finalWallet != null}, address=$finalAddress")
                    uiScope {
                        promise.reject("COA_CREATION_ERROR", "Failed to create COA account: transaction ID is null. Wallet: ${finalWallet != null}, Address: ${finalAddress.isNullOrBlank()}")
                    }
                    return@ioScope
                }

                logd(TAG, "createLinkedCOAAccount() - COA account creation transaction submitted: $txId")

                uiScope {
                    promise.resolve(txId)
                }
            } catch (e: Exception) {
                loge(TAG, "createLinkedCOAAccount() - error: ${e.message}")
                e.printStackTrace()
                uiScope {
                    promise.reject("COA_CREATION_ERROR", "Failed to create linked COA account: ${e.message}", e)
                }
            }
        }
    }

    fun generateSeedPhrase(strength: Double?, promise: Promise, bridgeModelToWritableMap: (Any) -> WritableMap) {
        // Default to 128 (12 words) if strength is not provided
        val strengthInt = (strength?.toInt() ?: 128)
        logd(TAG, "generateSeedPhrase() called - strength: $strengthInt")
        ioScope {
            try {
                // Use flow-wallet-kit BIP39 to generate mnemonic
                val length = when (strengthInt) {
                    128 -> BIP39.SeedPhraseLength.TWELVE
                    160 -> BIP39.SeedPhraseLength.FIFTEEN
                    256 -> BIP39.SeedPhraseLength.TWENTY_FOUR
                    else -> BIP39.SeedPhraseLength.TWELVE // Default to 12 words
                }
                val mnemonic = BIP39.generate(length, "")
                
                logd(TAG, "generateSeedPhrase() - Generated mnemonic with ${mnemonic.split(" ").size} words")
                
                // Create SeedPhraseKey from mnemonic to derive account key
                val baseDir = java.io.File(com.flowfoundation.wallet.utils.Env.getApp().filesDir, "wallet")
                val storage = com.flow.wallet.storage.FileSystemStorage(baseDir)
                
                // Use Flow derivation path: m/44'/539'/0'/0/0
                val derivationPath = "m/44'/539'/0'/0/0"
                
                val seedPhraseKey = com.flow.wallet.keys.SeedPhraseKey(
                    mnemonicString = mnemonic,
                    passphrase = "",
                    derivationPath = derivationPath,
                    keyPair = null,
                    storage = storage
                )
                
                // Derive public key using ECDSA_secp256k1 (matches EOA flow default)
                val publicKeyBytes = seedPhraseKey.publicKey(org.onflow.flow.models.SigningAlgorithm.ECDSA_secp256k1)
                if (publicKeyBytes == null) {
                    throw IllegalStateException("Failed to get public key from seed phrase key")
                }
                
                // Convert public key bytes to hex string (remove 0x04 prefix if present)
                val publicKeyHex = publicKeyBytes.toHexString().removePrefix("04")
                
                logd(TAG, "generateSeedPhrase() - Derived public key: ${publicKeyHex.take(16)}...")
                
                // Create AccountKey response
                // ECDSA_secp256k1 = sign_algo 2, SHA2_256 = hash_algo 1 (matches extension defaults)
                val accountKey = RNBridge.AccountKey(
                    publicKey = publicKeyHex,
                    hashAlgoStr = "SHA2_256",
                    signAlgoStr = "ECDSA_secp256k1",
                    weight = 1000, // Standard weight for Flow accounts
                    hashAlgo = 1, // SHA2_256
                    signAlgo = 2  // ECDSA_secp256k1
                )
                
                // Create SPResponse
                val response = RNBridge.SPResponse(
                    mnemonic = mnemonic,
                    accountKey = accountKey,
                    drivepath = derivationPath
                )
                
                // Convert to WritableMap for React Native
                val result = bridgeModelToWritableMap(response)
                
                logd(TAG, "generateSeedPhrase() - Successfully generated seed phrase and account key")
                uiScope {
                    promise.resolve(result)
                }
            } catch (e: Exception) {
                loge(TAG, "generateSeedPhrase() - error: ${e.message}")
                e.printStackTrace()
                uiScope {
                    promise.reject("GENERATE_SEED_PHRASE_ERROR", "Failed to generate seed phrase: ${e.message}", e)
                }
            }
        }
    }

    fun signOutAndSignInAnonymously(promise: Promise) {
        logd(TAG, "signOutAndSignInAnonymously() called")
        ioScope {
            try {
                val auth = Firebase.auth
                val currentUser = auth.currentUser
                
                if (currentUser != null) {
                    logd(TAG, "signOutAndSignInAnonymously() - Signing out current user: ${currentUser.uid}")
                    auth.signOut()
                    logd(TAG, "signOutAndSignInAnonymously() - Signed out successfully")
                } else {
                    logd(TAG, "signOutAndSignInAnonymously() - No current user to sign out")
                }
                
                // Sign in anonymously
                logd(TAG, "signOutAndSignInAnonymously() - Signing in anonymously...")
                auth.signInAnonymously().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val anonymousUser = auth.currentUser
                        logd(TAG, "signOutAndSignInAnonymously() - Anonymous sign-in successful, UID: ${anonymousUser?.uid}")
                        
                        // Wait for ID token to be available (ensures token is refreshed and ready)
                        anonymousUser?.getIdToken(true)?.addOnCompleteListener { tokenTask ->
                            if (tokenTask.isSuccessful && tokenTask.result != null) {
                                val token = tokenTask.result.token
                                if (token != null && token.isNotEmpty()) {
                                    logd(TAG, "signOutAndSignInAnonymously() - ID token obtained successfully (length: ${token.length})")
                                    uiScope {
                                        promise.resolve(null)
                                    }
                                } else {
                                    loge(TAG, "signOutAndSignInAnonymously() - ID token is null or empty")
                                    uiScope {
                                        promise.reject("TOKEN_ERROR", "ID token is null or empty")
                                    }
                                }
                            } else {
                                val exception = tokenTask.exception
                                val errorMessage = exception?.message ?: "Failed to get ID token"
                                loge(TAG, "signOutAndSignInAnonymously() - Failed to get ID token: $errorMessage")
                                uiScope {
                                    promise.reject("TOKEN_ERROR", errorMessage, exception)
                                }
                            }
                        }
                    } else {
                        val exception = task.exception
                        val errorMessage = exception?.message ?: "Anonymous sign-in failed"
                        loge(TAG, "signOutAndSignInAnonymously() - Failed: $errorMessage")
                        uiScope {
                            promise.reject("ANONYMOUS_SIGN_IN_ERROR", errorMessage, exception)
                        }
                    }
                }
            } catch (e: Exception) {
                loge(TAG, "signOutAndSignInAnonymously() - error: ${e.message}")
                e.printStackTrace()
                uiScope {
                    promise.reject("SIGN_OUT_ERROR", e.message ?: "Unknown error", e)
                }
            }
        }
    }

    fun signInWithCustomToken(customToken: String, promise: Promise) {
        logd(TAG, "signInWithCustomToken() called")
        ioScope {
            try {
                com.flowfoundation.wallet.firebase.auth.firebaseCustomLogin(customToken) { isSuccessful, exception ->
                    if (isSuccessful) {
                        logd(TAG, "signInWithCustomToken() - Custom token authentication successful")
                        uiScope {
                            promise.resolve(null)
                        }
                    } else {
                        val errorMessage = exception?.message ?: "Custom token authentication failed"
                        loge(TAG, "signInWithCustomToken() - Failed: $errorMessage")
                        uiScope {
                            promise.reject("CUSTOM_TOKEN_AUTH_ERROR", errorMessage, exception)
                        }
                    }
                }
            } catch (e: Exception) {
                loge(TAG, "signInWithCustomToken() - error: ${e.message}")
                e.printStackTrace()
                uiScope {
                    promise.reject("CUSTOM_TOKEN_ERROR", e.message ?: "Unknown error", e)
                }
            }
        }
    }

    fun saveMnemonic(mnemonic: String, customToken: String, txId: String, username: String, promise: Promise) {
        logd(TAG, "saveMnemonic() called - EOA account initialization")
        logd(TAG, "saveMnemonic() - txId: $txId, username: $username")

        ioScope {
            try {
                // Step 8: Securely store the mnemonic
                val prefix = storeMnemonicSecurely(mnemonic)

                // Step 9: Authenticate with Firebase
                authenticateWithFirebase(
                    customToken = customToken,
                    onSuccess = {
                        ioScope {
                            try {
                                // Step 10: Initialize Wallet-Kit
                                val seedPhraseKey = initializeWalletKit(mnemonic, prefix)

                                // Fetch user info and wallet list from backend
                                val service = com.flowfoundation.wallet.network.retrofit()
                                    .create(com.flowfoundation.wallet.network.ApiService::class.java)
                                val userInfoResponse = service.userInfo()
                                val walletListResponse = service.getWalletList()
                                
                                val userInfo = userInfoResponse.data
                                val walletListData = walletListResponse.data
                                    ?: throw IllegalStateException("No wallet data found")

                                // Preserve original username capitalization (backend API may return lowercase)
                                // Use the username passed from React Native which has proper capitalization
                                // Create a new UserInfoData with the original username
                                val userInfoWithOriginalUsername = UserInfoData(
                                    nickname = userInfo.nickname,
                                    username = username, // Use original capitalization
                                    avatar = userInfo.avatar,
                                    address = userInfo.address,
                                    isPrivate = userInfo.isPrivate,
                                    created = userInfo.created
                                )
                                logd(TAG, "saveMnemonic() - Preserved original username capitalization: $username (backend returned: ${userInfo.username})")

                                // Step 11: Fast account discovery using txId
                                discoverAccountFast(seedPhraseKey, txId, walletListData)

                                // Cache EOA address immediately from seedPhraseKey (before wallet initialization)
                                // This ensures the EOA address is available when getWalletAccounts() is called
                                var eoaAddress: String? = null
                                try {
                                    val baseDir = java.io.File(com.flowfoundation.wallet.utils.Env.getApp().filesDir, "wallet")
                                    val storage = com.flow.wallet.storage.FileSystemStorage(baseDir)
                                    val tempWallet = com.flow.wallet.wallet.WalletFactory.createKeyWallet(
                                        seedPhraseKey,
                                        setOf(org.onflow.flow.ChainId.Mainnet, org.onflow.flow.ChainId.Testnet),
                                        storage
                                    )
                                    eoaAddress = tempWallet.ethAddress(0)
                                    WalletManager.cacheEOAAddressSync(eoaAddress)
                                    logd(TAG, "saveMnemonic() - EOA address cached immediately: $eoaAddress")
                                } catch (e: Exception) {
                                    logw(TAG, "saveMnemonic() - Warning: Could not cache EOA address immediately: ${e.message}")
                                }

                                // Setup AccountManager and WalletManager
                                // Use userInfoWithOriginalUsername to preserve proper capitalization
                                val cryptoProvider = setupAccountAndWallet(prefix, userInfoWithOriginalUsername, walletListData)
                                
                                // Initialize EVMWalletManager to fetch COA address
                                com.flowfoundation.wallet.manager.evm.EVMWalletManager.updateEVMAddress()
                                
                                // Add EOA address to evmAddressMap after account is created and COA is fetched
                                // This ensures it shows up in the sidebar with its own emoji
                                if (eoaAddress != null) {
                                    try {
                                        // Wait a bit for EVMWalletManager to initialize
                                        kotlinx.coroutines.delay(500)
                                        
                                        val currentEvmMap = AccountManager.evmAddressData()?.evmAddressMap?.toMutableMap() ?: mutableMapOf()
                                        logd(TAG, "saveMnemonic() - Current evmAddressMap before adding EOA: $currentEvmMap")
                                        
                                        currentEvmMap[""] = eoaAddress // Empty string key for EOA address (matches convention)
                                        AccountManager.updateEVMAddressInfo(currentEvmMap)
                                        
                                        logd(TAG, "saveMnemonic() - EOA address added to evmAddressMap: $eoaAddress")
                                        logd(TAG, "saveMnemonic() - Updated evmAddressMap: $currentEvmMap")
                                        
                                        // Re-initialize AccountEmojiManager to generate walletEmojiList with the EOA address
                                        // This creates emoji assignments for all addresses including the newly added EOA
                                        com.flowfoundation.wallet.manager.emoji.AccountEmojiManager.init()
                                        logd(TAG, "saveMnemonic() - AccountEmojiManager re-initialized to include EOA address")
                                    } catch (e: Exception) {
                                        logw(TAG, "saveMnemonic() - Warning: Could not add EOA address to evmAddressMap: ${e.message}")
                                        e.printStackTrace()
                                    }
                                }

                                // Mark user as registered so app knows they've completed onboarding
                                com.flowfoundation.wallet.utils.setRegistered()
                                logd(TAG, "saveMnemonic() - User marked as registered")

                                // Track account creation
                                trackAccountCreation(cryptoProvider)

                                logd(TAG, "saveMnemonic() - EOA account initialization complete!")
                                
                                // Wait for wallet info to be populated with Flow address
                                // This ensures the account is ready for COA creation
                                logd(TAG, "saveMnemonic() - Waiting for wallet info to be populated...")
                                var waitRetries = 0
                                val maxWaitRetries = 30 // 15 seconds max
                                val currentNetwork = com.flowfoundation.wallet.manager.app.chainNetWorkString()
                                
                                while (waitRetries < maxWaitRetries) {
                                    val currentAccount = AccountManager.get()
                                    val flowAddress = currentAccount?.getFlowAddress(currentNetwork, TAG)
                                    
                                    if (!flowAddress.isNullOrBlank()) {
                                        logd(TAG, "saveMnemonic() - Flow address populated: $flowAddress")
                                        break
                                    }
                                    
                                    logd(TAG, "saveMnemonic() - Waiting for Flow address... (attempt ${waitRetries + 1}/$maxWaitRetries)")
                                    kotlinx.coroutines.delay(500)
                                    waitRetries++
                                    
                                    // Try to update wallet info from WalletFetcher
                                    if (waitRetries % 5 == 0) {
                                        logd(TAG, "saveMnemonic() - Triggering WalletFetcher to refresh wallet data")
                                        com.flowfoundation.wallet.manager.account.WalletFetcher.fetch()
                                    }
                                }
                                
                                val finalAccount = AccountManager.get()
                                val finalFlowAddress = finalAccount?.getFlowAddress(currentNetwork, TAG)
                                if (finalFlowAddress.isNullOrBlank()) {
                                    logw(TAG, "saveMnemonic() - Flow address not populated after waiting, but continuing anyway")
                                } else {
                                    logd(TAG, "saveMnemonic() - Account ready with Flow address: $finalFlowAddress")
                                }
                                
                                // Step 12: Close React Native view (handled by caller)
                                // Step 13: Notification permission (handled by caller)

                                uiScope {
                                    promise.resolve(null)
                                }
                            } catch (e: Exception) {
                                loge(TAG, "saveMnemonic() - Wallet initialization error: ${e.message}")
                                e.printStackTrace()
                                uiScope {
                                    promise.reject("WALLET_INIT_ERROR", "Wallet initialization failed: ${e.message}", e)
                                }
                            }
                        }
                    },
                    onFailure = { errorMessage ->
                            loge(TAG, "saveMnemonic() - Firebase authentication failed")
                            uiScope {
                            promise.reject("FIREBASE_AUTH_ERROR", errorMessage)
                            }
                        }
                )
            } catch (e: Exception) {
                loge(TAG, "saveMnemonic() - error: ${e.message}")
                e.printStackTrace()
                uiScope {
                    promise.reject("SAVE_MNEMONIC_ERROR", e.message ?: "Unknown error", e)
                }
            }
        }
    }

    private fun storeMnemonicSecurely(mnemonic: String): String {
        logd(TAG, "storeMnemonicSecurely() - Storing mnemonic securely...")

                val passwordMap = try {
                    val pref = com.flowfoundation.wallet.utils.readWalletPassword()
                    if (pref.isBlank()) {
                        HashMap<String, String>()
                    } else {
                        Gson().fromJson(pref, object : com.google.gson.reflect.TypeToken<HashMap<String, String>>() {}.type)
                    }
                } catch (e: Exception) {
                    HashMap<String, String>()
                }

                // Generate a unique prefix for this EOA account
                val prefix = com.flowfoundation.wallet.network.generatePrefix("eoa")

                // Store mnemonic globally for backup support
                com.flowfoundation.wallet.utils.storeWalletPassword(
                    Gson().toJson(passwordMap.apply { put("global", mnemonic) })
                )
        logd(TAG, "storeMnemonicSecurely() - Mnemonic stored securely")

        return prefix
    }


private fun authenticateWithFirebase(
        customToken: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        logd(TAG, "authenticateWithFirebase() - Checking current Firebase auth state...")
        
        val currentUser = Firebase.auth.currentUser
        val isAnonymous = currentUser?.isAnonymous ?: true
        
        // If already authenticated with non-anonymous user, skip authentication
        // This happens when signInWithCustomToken() was called before saveMnemonic()
        if (currentUser != null && !isAnonymous) {
            logd(TAG, "authenticateWithFirebase() - Already authenticated with non-anonymous user (UID: ${currentUser.uid}), skipping authentication")
            onSuccess()
            return
        }
        
        logd(TAG, "authenticateWithFirebase() - Starting Firebase authentication...")

        // Delete existing Firebase token and user (only if anonymous or no user)
                com.google.firebase.messaging.FirebaseMessaging.getInstance().deleteToken()
        if (currentUser != null) {
            currentUser.delete()?.addOnCompleteListener {
                logd(TAG, "authenticateWithFirebase() - Previous Firebase user deleted")
            }
                }

                // Sign in with custom token
        com.flowfoundation.wallet.firebase.auth.firebaseCustomLogin(customToken) { isSuccessful, exception ->
                        if (isSuccessful) {
                logd(TAG, "authenticateWithFirebase() - Firebase authentication successful")
                onSuccess()
            } else {
                val errorMessage = exception?.message ?: "Firebase authentication failed"
                loge(TAG, "authenticateWithFirebase() - Failed: $errorMessage")
                onFailure(errorMessage)
            }
        }
    }

private suspend fun initializeWalletKit(mnemonic: String, prefix: String): com.flow.wallet.keys.SeedPhraseKey {
        logd(TAG, "initializeWalletKit() - Creating SeedPhraseKey from mnemonic...")

                                val baseDir = java.io.File(com.flowfoundation.wallet.utils.Env.getApp().filesDir, "wallet")
                                val storage = com.flow.wallet.storage.FileSystemStorage(baseDir)

                                // Create SeedPhraseKey from mnemonic (same pattern as other restore flows)
                                val seedPhraseKey = com.flow.wallet.keys.SeedPhraseKey(
                                    mnemonicString = mnemonic,
                                    passphrase = "",
                                    derivationPath = "m/44'/539'/0'/0/0",
                                    keyPair = null,
                                    storage = storage
                                )

        logd(TAG, "initializeWalletKit() - SeedPhraseKey created")

        // Validate public key can be extracted (using ECDSA_secp256k1 as that's what we registered with)
        val publicKeyBytes = seedPhraseKey.publicKey(org.onflow.flow.models.SigningAlgorithm.ECDSA_secp256k1)
                                if (publicKeyBytes == null) {
                                    throw IllegalStateException("Failed to get public key from seed phrase key")
                                }

        logd(TAG, "initializeWalletKit() - Public key validated successfully")

        // Derive private key bytes from SeedPhraseKey and store as PrivateKey for CryptoProviderManager
        // CryptoProviderManager expects a PrivateKey stored with ID "prefix_key_${prefix}"
        val privateKeyBytes = seedPhraseKey.privateKey(org.onflow.flow.models.SigningAlgorithm.ECDSA_secp256k1)
        if (privateKeyBytes == null) {
            throw IllegalStateException("Failed to get private key from seed phrase key")
        }

        logd(TAG, "initializeWalletKit() - Derived private key bytes, creating PrivateKey...")

        // Create PrivateKey from the derived bytes
        val privateKey = com.flow.wallet.keys.PrivateKey.create(storage)
        privateKey.importPrivateKey(privateKeyBytes, com.flow.wallet.keys.KeyFormat.RAW)

        // Store the PrivateKey with prefix so CryptoProviderManager can find it
        val keyId = "prefix_key_$prefix"
        privateKey.store(keyId, prefix)
        logd(TAG, "initializeWalletKit() - PrivateKey stored successfully")

        return seedPhraseKey
    }

private suspend fun discoverAccountFast(
        seedPhraseKey: com.flow.wallet.keys.SeedPhraseKey,
        txId: String,
        walletListData: WalletListData
    ) {
        logd(TAG, "discoverAccountFast() - Discovering account using txId: $txId")

        val baseDir = java.io.File(com.flowfoundation.wallet.utils.Env.getApp().filesDir, "wallet")
        val storage = com.flow.wallet.storage.FileSystemStorage(baseDir)

        // Initialize Wallet SDK with account from Flow network
        val walletForSDK = com.flow.wallet.wallet.WalletFactory.createKeyWallet(
            seedPhraseKey,
            setOf(org.onflow.flow.ChainId.Mainnet, org.onflow.flow.ChainId.Testnet),
            storage
        )

        // Use txId to fetch account from Flow network for fast discovery
        // Note: walletListData might not have addresses yet if transaction hasn't finalized
        // In that case, the wallet will discover accounts automatically after transaction finalizes
        walletListData.wallets?.forEach { walletData ->
            walletData.blockchain?.forEach { blockchain ->
                try {
                    val chainIdForBlockchain = when (blockchain.chainId.lowercase()) {
                        "mainnet" -> org.onflow.flow.ChainId.Mainnet
                        "testnet" -> org.onflow.flow.ChainId.Testnet
                        else -> null
                    }
                    if (chainIdForBlockchain != null && blockchain.address.isNotBlank()) {
                        val address = if (blockchain.address.startsWith("0x")) {
                            blockchain.address
                        } else {
                            "0x${blockchain.address}"
                        }
                        logd(TAG, "discoverAccountFast() - Fetching account $address using txId: $txId")
                        walletForSDK.fetchAccountByAddress(address, chainIdForBlockchain)
                        logd(TAG, "discoverAccountFast() - Account fetched successfully")
                    } else if (chainIdForBlockchain != null) {
                        logd(TAG, "discoverAccountFast() - Address not available yet for ${blockchain.chainId}, wallet will discover after transaction finalizes")
                    }
                } catch (e: Exception) {
                    logd(TAG, "discoverAccountFast() - Warning: Could not fetch account: ${e.message}")
                }
            }
        }
        
        // If no addresses were found in walletListData, log that wallet will discover automatically
        val hasAddresses = walletListData.wallets?.any { walletData ->
            walletData.blockchain?.any { blockchain -> blockchain.address.isNotBlank() } == true
        } == true
        
        if (!hasAddresses) {
            logd(TAG, "discoverAccountFast() - No addresses in walletListData yet (transaction may not be finalized). Wallet will discover accounts automatically.")
        }
    }

private fun setupAccountAndWallet(
        prefix: String,
        userInfo: UserInfoData,
        walletListData: WalletListData
    ): CryptoProvider {
        logd(TAG, "setupAccountAndWallet() - Setting up AccountManager and WalletManager...")

                                // Add account to AccountManager
                                AccountManager.add(
                                    Account(
                                        userInfo = userInfo,
                                        prefix = prefix,
                                        wallet = walletListData
                                    ),
                                    com.flowfoundation.wallet.firebase.auth.firebaseUid()
                                )
        logd(TAG, "setupAccountAndWallet() - Account added to AccountManager")

                                // Initialize WalletManager
                                WalletManager.init()
        logd(TAG, "setupAccountAndWallet() - WalletManager initialized")

        // Get crypto provider for the current account
                                val currentAccount = AccountManager.get()
            ?: throw IllegalStateException("Account not found after adding to AccountManager")

        val cryptoProvider = CryptoProviderManager.generateAccountCryptoProvider(currentAccount)
            ?: throw IllegalStateException("Failed to generate crypto provider")

        logd(TAG, "setupAccountAndWallet() - Crypto provider generated")

        return cryptoProvider
    }

    /**
     * Track account creation analytics and clear cache
     */
    private suspend fun trackAccountCreation(cryptoProvider: CryptoProvider) {
        logd(TAG, "trackAccountCreation() - Tracking account creation...")

        // Track account creation analytics
                                com.flowfoundation.wallet.mixpanel.MixpanelManager.accountCreated(
            cryptoProvider.getPublicKey(),
                                    com.flowfoundation.wallet.mixpanel.AccountCreateKeyType.KEY_STORE,
                                    cryptoProvider.getSignatureAlgorithm().value,
                                    cryptoProvider.getHashAlgorithm().algorithm
                                )

                                // Clear cache
                                com.flowfoundation.wallet.network.clearUserCache()
        logd(TAG, "trackAccountCreation() - Account creation tracked and cache cleared")
    }

}
