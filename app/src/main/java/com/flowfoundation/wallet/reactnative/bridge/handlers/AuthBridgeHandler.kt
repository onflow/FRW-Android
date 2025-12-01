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

    fun registerSecureTypeAccount(username: String, promise: Promise, sendEvent: (String, WritableMap?) -> Unit) {
        logd(TAG, "registerSecureTypeAccount() called - Registering Secure Type Account (Secure Enclave)")
        logd(TAG, "registerSecureTypeAccount() - username: $username")
        ioScope {
            try {
                // Send progress: 0% - Starting
                sendProgressEvent(sendEvent, 0, "Starting account creation")

                logd(TAG, "registerSecureTypeAccount() - starting account registration...")

                // Send progress: 20% - Generating keys
                sendProgressEvent(sendEvent, 20, "Generating secure keys")

                // Use the existing registerOutblock function which creates a Secure Type/COA account:
                // - Generates keys (secure enclave on supported devices)
                // - Registers with backend server
                // - Creates Flow blockchain account
                // - Sets up Firebase authentication
                // - Creates local Account in AccountManager
                
                // Send progress: 40% - Registering with backend
                sendProgressEvent(sendEvent, 40, "Registering with backend")
                
                val success = com.flowfoundation.wallet.network.registerOutblock(username)

                if (success) {
                    // Send progress: 80% - Account created
                    sendProgressEvent(sendEvent, 80, "Account created successfully")
                    
                    logd(TAG, "registerSecureTypeAccount() - account created successfully")

                    // Get the created account details
                    val account = AccountManager.get()
                    val address = WalletManager.selectedWalletAddress()

                    // Note: Secure Type accounts use hardware-backed keys (Secure Enclave)
                    // No mnemonic is generated or stored for these accounts

                    // Send progress: 100% - Complete
                    sendProgressEvent(sendEvent, 100, "Account ready")

                    // Small delay to ensure progress events are delivered before promise resolves
                    kotlinx.coroutines.delay(100)

                    // Create success response using WritableMap
                    val response = WritableNativeMap()
                    response.putBoolean("success", true)
                    response.putString("address", address)
                    response.putString("username", account?.userInfo?.username ?: username)
                    response.putString("accountType", "hardware") // Secure enclave uses hardware-backed keys
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
                    response.putString("accountType", "hardware") // Secure enclave uses hardware-backed keys
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
                response.putString("accountType", "hardware") // Secure enclave uses hardware-backed keys
                response.putString("error", e.message ?: "Unknown error")

                uiScope {
                    promise.resolve(response)
                }
            }
        }
    }

    fun registerAccountWithBackend(promise: Promise) {
        logd(TAG, "registerAccountWithBackend() called - Linking COA account on-chain for Recovery Phrase flow")
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

                    if (wallet != null || selectedAddress.isNotBlank()) {
                        logd(TAG, "registerAccountWithBackend() - Wallet is ready (attempt ${retries + 1})")
                        break
                    }

                    retries++
                    if (retries < maxRetries) {
                        logd(TAG, "registerAccountWithBackend() - Wallet not ready yet, waiting... (attempt $retries/$maxRetries)")
                        kotlinx.coroutines.delay(retryDelayMs)
                    }
                }

                // Verify wallet is ready
                val finalWallet = WalletManager.wallet()
                val finalAddress = WalletManager.selectedWalletAddress()

                if (finalWallet == null && finalAddress.isBlank()) {
                    loge(TAG, "registerAccountWithBackend() - Wallet not ready after $maxRetries attempts")
                    uiScope {
                        promise.reject("COA_CREATION_ERROR", "Wallet not ready: cannot link COA account on-chain. Wallet: ${false}, Address: ${finalAddress.isNullOrBlank()}")
                    }
                    return@ioScope
                }

                logd(TAG, "registerAccountWithBackend() - Wallet ready, linking COA account on-chain...")
                logd(TAG, "registerAccountWithBackend() - Final wallet: ${finalWallet != null}, Final address: $finalAddress")

                // Wait for the exact account created by saveMnemonic to have its Flow address populated
                // After saveMnemonic, the account is set as current via AccountManager
                // We must use AccountManager.get() to get the exact account that was just created
                // and wait for its Flow address to be populated from Account.wallet data
                var accountRetries = 0
                val maxAccountRetries = 30 // Increased retries to allow more time for transaction finalization
                val accountRetryDelayMs = 500L // Increased delay to allow transaction to finalize
                val currentNetwork = com.flowfoundation.wallet.manager.app.chainNetWorkString()

                var finalAccount: Account? = null
                var finalAccountAddress: String? = null

                while (accountRetries < maxAccountRetries) {
                    // Get the exact current account - this is the account that was just created by saveMnemonic
                    val currentAccount = AccountManager.get()

                    if (currentAccount == null) {
                        logd(TAG, "registerAccountWithBackend() - Current account not available yet (attempt ${accountRetries + 1})")
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
                                    val discoveredAddress = flowAccount.address
                                    if (discoveredAddress.isNotBlank()) {
                                        flowAddress = discoveredAddress
                                        logd(TAG, "registerAccountWithBackend() - Found Flow address from wallet SDK discovered accounts: $flowAddress")
                                    }
                                }
                            }
                        }
                    }

                    if (flowAddress != null && flowAddress.isNotBlank()) {
                        // Found the Flow address for the exact account
                        finalAccount = currentAccount
                        finalAccountAddress = flowAddress
                        logd(TAG, "registerAccountWithBackend() - Found Flow address for current account (attempt ${accountRetries + 1})")
                        logd(TAG, "registerAccountWithBackend() - Account username: ${currentAccount.userInfo.username}, prefix: ${currentAccount.prefix}, address: $flowAddress")
                        break
                    } else {
                        // Account exists but Flow address not populated yet - continue waiting
                        logd(TAG, "registerAccountWithBackend() - Current account exists (${currentAccount.userInfo.username}) but Flow address not available yet (attempt ${accountRetries + 1})")
                        logd(TAG, "registerAccountWithBackend() - Waiting for Flow address to be discovered...")
                        val wallet = WalletManager.wallet()
                        if (wallet != null) {
                            val chainId = when (currentNetwork.lowercase()) {
                                "mainnet" -> org.onflow.flow.ChainId.Mainnet
                                "testnet" -> org.onflow.flow.ChainId.Testnet
                                else -> null
                            }
                            val walletAccounts = chainId?.let { wallet.accounts[it] }
                            logd(TAG, "registerAccountWithBackend() - Wallet accounts for $currentNetwork: ${walletAccounts?.size ?: 0}")
                        } else {
                            logd(TAG, "registerAccountWithBackend() - WalletManager.wallet() is null")
                        }
                    }

                    accountRetries++
                    if (accountRetries < maxAccountRetries) {
                        kotlinx.coroutines.delay(accountRetryDelayMs)
                    }
                }

                if (finalAccount == null || finalAccountAddress == null) {
                    val currentAccount = AccountManager.get()
                    loge(TAG, "registerAccountWithBackend() - Flow address not found for current account after $maxAccountRetries attempts")
                    if (currentAccount != null) {
                        val flowAddress = currentAccount.getFlowAddress(currentNetwork, TAG)
                        loge(TAG, "registerAccountWithBackend() - Current account: username=${currentAccount.userInfo.username}, prefix=${currentAccount.prefix}, address=$flowAddress")
                        loge(TAG, "registerAccountWithBackend() - Account.wallet is null: ${currentAccount.wallet == null}")
                    } else {
                        loge(TAG, "registerAccountWithBackend() - Current account is null")
                    }
                    uiScope {
                        promise.reject("COA_CREATION_ERROR", "Account Flow address not available: cannot link COA account on-chain. Current account Flow address not populated.")
                    }
                    return@ioScope
                }

                logd(TAG, "registerAccountWithBackend() - Account verified with Flow address: $finalAccountAddress, proceeding with COA link transaction...")

                // Check if COA account already exists before creating one
                // This prevents errors if COA account was already created (e.g., in Secure Enclave flow)
                val coaAlreadyExists = try {
                    com.flowfoundation.wallet.manager.flowjvm.cadenceCheckCOALink(finalAccountAddress)
                } catch (e: Exception) {
                    logw(TAG, "registerAccountWithBackend() - Could not check COA link status: ${e.message}")
                    null // If check fails, proceed with link attempt
                }

                if (coaAlreadyExists == true) {
                    logd(TAG, "registerAccountWithBackend() - COA account already linked for address $finalAccountAddress, skipping")
                    uiScope {
                        // Return a success response indicating COA already exists
                        // This allows the flow to continue without error
                        promise.resolve("COA_ALREADY_EXISTS")
                    }
                    return@ioScope
                }

                // Execute Cadence transaction to link COA account on-chain
                val txId = try {
                    com.flowfoundation.wallet.manager.flowjvm.cadenceCreateCOAAccount()
                } catch (e: Exception) {
                    // Check if error is due to COA already existing
                    val errorMessage = e.message?.lowercase() ?: ""
                    if (errorMessage.contains("already") || errorMessage.contains("exists") || errorMessage.contains("duplicate")) {
                        logd(TAG, "registerAccountWithBackend() - COA account already linked (detected from error), skipping")
                        uiScope {
                            promise.resolve("COA_ALREADY_EXISTS")
                        }
                        return@ioScope
                    }
                    loge(TAG, "registerAccountWithBackend() - Exception calling cadenceCreateCOAAccount: ${e.message}")
                    e.printStackTrace()
                    null
                }

                if (txId.isNullOrBlank()) {
                    loge(TAG, "registerAccountWithBackend() - Transaction ID is null or empty")
                    loge(TAG, "registerAccountWithBackend() - Wallet state: wallet=${finalWallet != null}, address=$finalAddress")
                    uiScope {
                        promise.reject("COA_CREATION_ERROR", "Failed to link COA account on-chain: transaction ID is null. Wallet: ${finalWallet != null}, Address: ${finalAddress.isBlank()}")
                    }
                    return@ioScope
                }

                logd(TAG, "registerAccountWithBackend() - COA link transaction submitted: $txId")

                uiScope {
                    promise.resolve(txId)
                }
            } catch (e: Exception) {
                loge(TAG, "registerAccountWithBackend() - error: ${e.message}")
                e.printStackTrace()
                uiScope {
                    promise.reject("COA_CREATION_ERROR", "Failed to link COA account on-chain: ${e.message}", e)
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
                // IMPORTANT: Use in-memory storage only - mnemonic is NOT confirmed yet
                // It will be saved to disk later when saveMnemonic() is called after user confirmation
                val inMemoryStorage = com.flow.wallet.storage.InMemoryStorage()

                // Use Flow derivation path: m/44'/539'/0'/0/0
                val derivationPath = "m/44'/539'/0'/0/0"

                val seedPhraseKey = com.flow.wallet.keys.SeedPhraseKey(
                    mnemonicString = mnemonic,
                    passphrase = "",
                    derivationPath = derivationPath,
                    keyPair = null,
                    storage = inMemoryStorage
                )

                // Derive public key using ECDSA_secp256k1 (matches EOA flow default)
                val publicKeyBytes = seedPhraseKey.publicKey(org.onflow.flow.models.SigningAlgorithm.ECDSA_secp256k1)
                  ?: throw IllegalStateException("Failed to get public key from seed phrase key")

                // Convert public key bytes to hex string
                val publicKeyHexRaw = publicKeyBytes.toHexString()

                // Remove 0x04 prefix if present (uncompressed public key format)
                // ECDSA secp256k1 uncompressed public keys are 65 bytes (130 hex chars) with 0x04 prefix
                // Only remove prefix if the key has the expected length and starts with "04"
                val publicKeyHex = if (publicKeyHexRaw.length == 130 && publicKeyHexRaw.startsWith("04")) {
                    publicKeyHexRaw.removePrefix("04")
                } else {
                    publicKeyHexRaw
                }

                logd(TAG, "generateSeedPhrase() - Derived public key (raw length: ${publicKeyHexRaw.length}, final length: ${publicKeyHex.length}): ${publicKeyHex.take(16)}...")

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
                                if (!token.isNullOrEmpty()) {
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

    fun saveMnemonic(mnemonic: String, customToken: String, txId: String, username: String, promise: Promise, sendEvent: (String, WritableMap?) -> Unit) {
        logd(TAG, "saveMnemonic() called - EOA account initialization")
        logd(TAG, "saveMnemonic() - txId: $txId, username: $username")

        ioScope {
            try {
                // Send progress: 0% - Starting
                sendProgressEvent(sendEvent, 0, "Starting account setup")

                // Clear in-memory caches from previous active account FIRST
                // This prevents stale data from appearing in the UI
                WalletManager.clear()
                com.flowfoundation.wallet.manager.evm.EVMWalletManager.clear()
                com.flowfoundation.wallet.manager.emoji.AccountEmojiManager.clear()
                logd(TAG, "saveMnemonic() - Cleared all in-memory caches before adding new account")

                // Step 8: Securely store the mnemonic
                sendProgressEvent(sendEvent, 5, "Securing recovery phrase")
                val prefix = storeMnemonicSecurely(mnemonic)

                // Step 9: Authenticate with Firebase
                sendProgressEvent(sendEvent, 10, "Authenticating account")
                authenticateWithFirebase(
                    customToken = customToken,
                    onSuccess = {
                        ioScope {
                            try {
                                // Force Firebase ID token refresh to get the new account's JWT
                                // This ensures API requests use the new account's credentials
                                sendProgressEvent(sendEvent, 15, "Verifying authentication")
                                logd(TAG, "saveMnemonic() - Forcing Firebase ID token refresh...")
                                var tokenRefreshed = false
                                var refreshAttempts = 0
                                val maxRefreshAttempts = 10
                                
                                while (!tokenRefreshed && refreshAttempts < maxRefreshAttempts) {
                                    kotlinx.coroutines.delay(500) // Wait 500ms between checks
                                    refreshAttempts++
                                    try {
                                        // Force refresh the token
                                        val jwt = com.flowfoundation.wallet.firebase.auth.getFirebaseJwt(forceRefresh = true)
                                        val currentUid = com.flowfoundation.wallet.firebase.auth.firebaseUid()
                                        
                                        if (!jwt.isNullOrBlank() && currentUid != null) {
                                            tokenRefreshed = true
                                            logd(TAG, "saveMnemonic() - Firebase ID token refreshed after $refreshAttempts attempt(s), UID: $currentUid")
                                            
                                            // Verify the username matches by making a test API call
                                            try {
                                                val testService = com.flowfoundation.wallet.network.retrofit()
                                                    .create(com.flowfoundation.wallet.network.ApiService::class.java)
                                                val testUserInfo = testService.userInfo().data
                                                logd(TAG, "saveMnemonic() - Token validated, backend returned username: ${testUserInfo.username}")
                                                
                                                // Check if username matches (case-insensitive, ignoring numeric suffix)
                                                // Backend normalizes to lowercase and adds suffix: "FancyRiverVolcano" -> "fancyrivervolcano_476"
                                                val backendUsernameBase = testUserInfo.username.substringBefore("_").lowercase()
                                                val expectedUsernameBase = username.lowercase()
                                                
                                                if (backendUsernameBase != expectedUsernameBase) {
                                                    logw(TAG, "saveMnemonic() - Username mismatch! Expected: $expectedUsernameBase, Got: $backendUsernameBase. Retrying...")
                                                    tokenRefreshed = false // Retry
                                                } else {
                                                    logd(TAG, "saveMnemonic() - Username validated: $expectedUsernameBase matches $backendUsernameBase")
                                                }
                                            } catch (e: Exception) {
                                                logw(TAG, "saveMnemonic() - Could not validate token with backend, continuing: ${e.message}")
                                            }
                                        } else {
                                            logd(TAG, "saveMnemonic() - Waiting for token refresh (attempt $refreshAttempts/$maxRefreshAttempts)")
                                        }
                                    } catch (e: Exception) {
                                        logd(TAG, "saveMnemonic() - Error during token refresh (attempt $refreshAttempts): ${e.message}")
                                    }
                                }
                                
                                if (!tokenRefreshed) {
                                    logw(TAG, "saveMnemonic() - Warning: Token may not be for correct user, proceeding anyway")
                                }

                                // Step 10: Initialize Wallet-Kit
                                sendProgressEvent(sendEvent, 25, "Initializing wallet")
                                val seedPhraseKey = initializeWalletKit(mnemonic, prefix)

                                // Fetch user info from backend
                                sendProgressEvent(sendEvent, 30, "Fetching account info")
                                val service = com.flowfoundation.wallet.network.retrofit()
                                    .create(com.flowfoundation.wallet.network.ApiService::class.java)
                                val userInfoResponse = service.userInfo()
                                val userInfo = userInfoResponse.data

                                // Create Flow account on-chain via backend API
                                sendProgressEvent(sendEvent, 35, "Creating Flow account")
                                logd(TAG, "saveMnemonic() - Creating Flow account via /v1/user/address...")
                                try {
                                    val createWalletResponse = service.createWallet()
                                    logd(TAG, "saveMnemonic() - Flow account creation initiated successfully")
                                } catch (e: Exception) {
                                    logw(TAG, "saveMnemonic() - Warning: Flow account creation API call failed: ${e.message}")
                                    // Continue anyway - account might already exist or will be created by another mechanism
                                }

                                // Wait for wallet address to be populated by server (may take a few seconds after account creation)
                                sendProgressEvent(sendEvent, 40, "Waiting for blockchain confirmation")
                                logd(TAG, "saveMnemonic() - Waiting for server to index Flow account address...")
                                var walletListData: com.flowfoundation.wallet.network.model.WalletListData? = null
                                var retries = 0
                                val maxRetries = 60 // 60 attempts (2 minutes total - dev server can be very slow)
                                val delayMs = 2000L // 2 seconds between attempts
                                
                                while (retries < maxRetries) {
                                    try {
                                        // Update progress gradually during polling (40% -> 65%)
                                        val pollingProgress = 40 + ((retries.toFloat() / maxRetries) * 25).toInt()
                                        if (retries % 5 == 0) { // Update every 5 attempts to avoid spamming
                                            sendProgressEvent(sendEvent, pollingProgress, "Indexing account on blockchain")
                                        }
                                        
                                        val fetchedData = service.getWalletList().data
                                        
                                        // Log detailed response structure
                                        logd(TAG, "saveMnemonic() - getWalletList response (attempt ${retries + 1}/$maxRetries):")
                                        logd(TAG, "  fetchedData is null: ${fetchedData == null}")
                                        logd(TAG, "  wallets count: ${fetchedData?.wallets?.size ?: 0}")
                                        
                                        fetchedData?.wallets?.forEachIndexed { idx, wallet ->
                                            logd(TAG, "  Wallet[$idx]:")
                                            logd(TAG, "    name: ${wallet.name}")
                                            logd(TAG, "    blockchain is null: ${wallet.blockchain == null}")
                                            logd(TAG, "    blockchain count: ${wallet.blockchain?.size ?: 0}")
                                            wallet.blockchain?.forEachIndexed { bIdx, blockchain ->
                                                logd(TAG, "      Blockchain[$bIdx]:")
                                                logd(TAG, "        chainId: '${blockchain.chainId}'")
                                                logd(TAG, "        address: '${blockchain.address}'")
                                                logd(TAG, "        address.isNotBlank(): ${blockchain.address.isNotBlank()}")
                                            }
                                        }
                                        
                                        // Check if blockchain addresses are populated
                                        val hasAddress = fetchedData?.wallets?.any { wallet ->
                                            val result = wallet.blockchain?.any { it.address.isNotBlank() } == true
                                            logd(TAG, "  Wallet '${wallet.name}' has address: $result")
                                            result
                                        } == true
                                        
                                        logd(TAG, "  Overall hasAddress: $hasAddress")
                                        
                                        if (hasAddress) {
                                            walletListData = fetchedData
                                            sendProgressEvent(sendEvent, 65, "Account indexed successfully")
                                            logd(TAG, "saveMnemonic() - Flow account address found after $retries retries (${retries * delayMs / 1000}s)")
                                            break
                                        } else {
                                            logd(TAG, "saveMnemonic() - Waiting for blockchain addresses to populate...")
                                            kotlinx.coroutines.delay(delayMs) // Wait before retry
                                            retries++
                                        }
                                    } catch (e: Exception) {
                                        logd(TAG, "saveMnemonic() - Error fetching wallet list (attempt ${retries + 1}): ${e.message}")
                                        e.printStackTrace()
                                        kotlinx.coroutines.delay(delayMs)
                                        retries++
                                    }
                                }

                                if (walletListData == null) {
                                    throw IllegalStateException("Failed to fetch wallet list with addresses after $maxRetries attempts")
                                }
                                
                                // Verify we have at least one address
                                val hasValidAddress = walletListData.wallets?.any { wallet ->
                                    wallet.blockchain?.any { it.address.isNotBlank() } == true
                                } == true
                                
                                if (!hasValidAddress) {
                                    throw IllegalStateException("Wallet data has no valid blockchain addresses after $maxRetries attempts")
                                }

                                // Send progress: 70% - Discovered account
                                sendProgressEvent(sendEvent, 70, "Configuring account")
                                
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
                                sendProgressEvent(sendEvent, 80, "Setting up wallet")
                                // Use userInfoWithOriginalUsername to preserve proper capitalization
                                val cryptoProvider = setupAccountAndWallet(prefix, userInfoWithOriginalUsername, walletListData)

                                // Add EOA address to evmAddressMap before fetching COA
                                // EVMWalletManager.updateEVMAddress() will merge with this, preserving both addresses
                                if (eoaAddress != null) {
                                    try {
                                        val evmMap = mutableMapOf<String, String>()
                                        evmMap[""] = eoaAddress // Empty string key for EOA address
                                        AccountManager.updateEVMAddressInfo(evmMap)
                                        logd(TAG, "saveMnemonic() - EOA address added to evmAddressMap: $eoaAddress")
                                    } catch (e: Exception) {
                                        logw(TAG, "saveMnemonic() - Warning: Could not add EOA address: ${e.message}")
                                        e.printStackTrace()
                                    }
                                }

                                // Initialize EVMWalletManager to fetch and add COA address
                                sendProgressEvent(sendEvent, 85, "Initializing EVM wallet")
                                // EVMWalletManager now preserves existing entries (like EOA) when adding COA
                                com.flowfoundation.wallet.manager.evm.EVMWalletManager.updateEVMAddress()

                                // Wait for COA fetch to complete, then re-initialize emoji manager
                                kotlinx.coroutines.delay(1000)

                                // Verify and log final state
                                if (eoaAddress != null) {
                                    try {
                                        val finalEvmMap = AccountManager.evmAddressData()?.evmAddressMap
                                        logd(TAG, "saveMnemonic() - Final evmAddressMap: $finalEvmMap")
                                        logd(TAG, "saveMnemonic() - Contains EOA: ${finalEvmMap?.containsKey("")}, Contains COA: ${(finalEvmMap?.size ?: 0) > 1}")

                                        // Re-initialize AccountEmojiManager to generate walletEmojiList with all addresses
                                        com.flowfoundation.wallet.manager.emoji.AccountEmojiManager.init()
                                        logd(TAG, "saveMnemonic() - AccountEmojiManager initialized with ${finalEvmMap?.size ?: 0} addresses")

                                        // Trigger wallet data update to refresh UI (e.g., drawer sidebar)
                                        // This ensures the sidebar shows all addresses including EOA immediately
                                        AccountManager.updateWalletInfo(walletListData)
                                        logd(TAG, "saveMnemonic() - Triggered UI refresh via updateWalletInfo")

                                        // Close the drawer to show the updated account in the main view
                                        com.flowfoundation.wallet.page.main.MainActivity.getInstance()?.closeDrawer()
                                        logd(TAG, "saveMnemonic() - Closed drawer to show updated account")
                                    } catch (e: Exception) {
                                        logw(TAG, "saveMnemonic() - Warning: Could not verify evmAddressMap: ${e.message}")
                                        e.printStackTrace()
                                    }
                                }

                                // Mark user as registered so app knows they've completed onboarding
                                sendProgressEvent(sendEvent, 90, "Finalizing account")
                                com.flowfoundation.wallet.utils.setRegistered()
                                logd(TAG, "saveMnemonic() - User marked as registered")

                                // Track account creation
                                sendProgressEvent(sendEvent, 95, "Completing setup")
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

                                // Send progress: 100% - Complete
                                sendProgressEvent(sendEvent, 100, "Account ready")
                                
                                // Small delay to ensure progress event is delivered before promise resolves
                                kotlinx.coroutines.delay(100)
                                
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
        val currentUid = currentUser?.uid
        val isAnonymous = currentUser?.isAnonymous ?: true

        if (currentUser != null) {
            logd(TAG, "authenticateWithFirebase() - Current user: UID=$currentUid, isAnonymous=$isAnonymous")
        }

        // If already authenticated with a non-anonymous user, we MUST sign out first
        // to switch to the new account. Firebase won't switch users without signing out.
        if (currentUser != null && !isAnonymous) {
            logd(TAG, "authenticateWithFirebase() - Signing out current user to switch accounts...")
            
            // Sign out the current user
            Firebase.auth.signOut()
            logd(TAG, "authenticateWithFirebase() - User signed out successfully")
            
            // Delete Firebase messaging token for the old user
            com.google.firebase.messaging.FirebaseMessaging.getInstance().deleteToken()
        } else if (isAnonymous) {
            // Delete anonymous user
            com.google.firebase.messaging.FirebaseMessaging.getInstance().deleteToken()
            currentUser?.delete()?.addOnCompleteListener {
                logd(TAG, "authenticateWithFirebase() - Previous anonymous user deleted")
            }
        }

        logd(TAG, "authenticateWithFirebase() - Signing in with new custom token...")

        // Sign in with the new account's custom token
        com.flowfoundation.wallet.firebase.auth.firebaseCustomLogin(customToken) { isSuccessful, exception ->
            if (isSuccessful) {
                val newUid = Firebase.auth.currentUser?.uid
                logd(TAG, "authenticateWithFirebase() - Firebase authentication successful, new UID: $newUid")
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
          ?: throw IllegalStateException("Failed to get public key from seed phrase key")

  logd(TAG, "initializeWalletKit() - Public key validated successfully")

        // Derive private key bytes from SeedPhraseKey and store as PrivateKey for CryptoProviderManager
        // CryptoProviderManager expects a PrivateKey stored with ID "prefix_key_${prefix}"
        val privateKeyBytes = seedPhraseKey.privateKey(org.onflow.flow.models.SigningAlgorithm.ECDSA_secp256k1)
          ?: throw IllegalStateException("Failed to get private key from seed phrase key")

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

                                // Clear in-memory caches from previous active account
                                // This prevents stale EOA/EVM data from appearing in the UI
                                // Note: We don't remove the old account - it stays for profile switching
                                WalletManager.clear() // Clears currentWallet, selectedAddress, and EOA cache
                                com.flowfoundation.wallet.manager.evm.EVMWalletManager.clear() // Clears evmAddressMap
                                com.flowfoundation.wallet.manager.emoji.AccountEmojiManager.clear() // Clears emoji cache
                                
                                logd(TAG, "setupAccountAndWallet() - Cleared all in-memory caches (old account preserved for switching)")

                                // Log wallet data structure for debugging
                                logd(TAG, "setupAccountAndWallet() - WalletListData: wallets count=${walletListData.wallets?.size}")
                                walletListData.wallets?.forEachIndexed { idx, wallet ->
                                    logd(TAG, "setupAccountAndWallet() -   Wallet $idx: name=${wallet.name}, blockchain count=${wallet.blockchain?.size}")
                                    wallet.blockchain?.forEach { blockchain ->
                                        logd(TAG, "setupAccountAndWallet() -     Blockchain: chainId=${blockchain.chainId}, address=${blockchain.address}")
                                    }
                                }

                                // Add account to AccountManager
                                AccountManager.add(
                                    Account(
                                        userInfo = userInfo,
                                        prefix = prefix,
                                        wallet = walletListData,
                                        evmAddressData = null // Explicitly set to null for new account (no EOA for secure enclave)
                                    ),
                                    com.flowfoundation.wallet.firebase.auth.firebaseUid()
                                )
        logd(TAG, "setupAccountAndWallet() - Account added to AccountManager")

                                // Reinitialize EVMWalletManager to load clean state from the new account
                                com.flowfoundation.wallet.manager.evm.EVMWalletManager.init()
                                logd(TAG, "setupAccountAndWallet() - EVMWalletManager reinitialized with clean state")

                                // Initialize AccountEmojiManager to generate emoji list for the new account
                                com.flowfoundation.wallet.manager.emoji.AccountEmojiManager.init()
                                logd(TAG, "setupAccountAndWallet() - AccountEmojiManager initialized for new account")

                                // Initialize WalletManager
                                WalletManager.init()
        logd(TAG, "setupAccountAndWallet() - WalletManager initialized")
                                
                                // Wait for WalletManager to initialize, then select the Flow address
                                WalletManager.onWalletReady {
                                    logd(TAG, "setupAccountAndWallet() - WalletManager ready callback triggered")
                                    
                                    // Get the Flow address from AccountManager (should be populated now)
                                    val currentAcct = AccountManager.get()
                                    
                                    // Find the first wallet that has a blockchain address (don't just use firstOrNull)
                                    val flowAddr = currentAcct?.wallet?.wallets
                                        ?.firstOrNull { wallet -> wallet.blockchain?.any { it.address.isNotBlank() } == true }
                                        ?.blockchain?.firstOrNull()?.address
                                    
                                    if (!flowAddr.isNullOrBlank()) {
                                        val formattedAddr = if (flowAddr.startsWith("0x")) flowAddr else "0x$flowAddr"
                                        WalletManager.selectWalletAddress(formattedAddr)
                                        logd(TAG, "setupAccountAndWallet() - Wallet ready: Selected Flow address: $formattedAddr")
                                        
                                        // Trigger UI update to refresh drawer with new account
                                        // This will update both the wallet data AND trigger drawer refresh
                                        com.flowfoundation.wallet.utils.uiScope {
                                            if (currentAcct.wallet != null) {
                                                AccountManager.updateWalletInfo(currentAcct.wallet!!)
                                                logd(TAG, "setupAccountAndWallet() - Wallet ready: Triggered drawer refresh via updateWalletInfo")
                                            }
                                            
                                            // Also explicitly refresh the drawer ViewModel
                                            val mainActivity = com.flowfoundation.wallet.page.main.MainActivity.getInstance()
                                            if (mainActivity != null) {
                                                try {
                                                    val viewModel = androidx.lifecycle.ViewModelProvider(mainActivity)[com.flowfoundation.wallet.page.main.drawer.DrawerLayoutViewModel::class.java]
                                                    viewModel.loadData()
                                                    logd(TAG, "setupAccountAndWallet() - Wallet ready: Explicitly refreshed drawer ViewModel")
                                                } catch (e: Exception) {
                                                    logd(TAG, "setupAccountAndWallet() - Wallet ready: Could not refresh drawer ViewModel: ${e.message}")
                                                }
                                            }
                                        }
                                    } else {
                                        logd(TAG, "setupAccountAndWallet() - Wallet ready: Warning - Flow address still not available")
                                    }
                                }

                                // Relaunch MainActivity to ensure all state is completely fresh
                                // This recreates all ViewModels and managers with the new account
                                com.flowfoundation.wallet.utils.uiScope {
                                    com.flowfoundation.wallet.page.main.MainActivity.relaunch(
                                        com.flowfoundation.wallet.utils.Env.getApp(), 
                                        clearTop = true
                                    )
                                }
                                logd(TAG, "setupAccountAndWallet() - Scheduled MainActivity relaunch for fresh state")

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

    /**
     * Send progress event to React Native
     * @param sendEvent The event sender function from the bridge
     * @param progress Progress percentage (0-100)
     * @param status Status message
     */
    private fun sendProgressEvent(sendEvent: (String, WritableMap?) -> Unit, progress: Int, status: String) {
        try {
            logd(TAG, "Attempting to send progress event: $progress% - $status")
            val params = WritableNativeMap()
            params.putInt("progress", progress)
            params.putString("status", status)
            sendEvent("AccountCreationProgress", params)
            logd(TAG, "Successfully sent progress event: $progress%")
        } catch (e: Exception) {
            loge(TAG, "Failed to send progress event $progress%: ${e.message}")
            e.printStackTrace()
        }
    }

}
