package com.flowfoundation.wallet.reactnative.bridge.handlers

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.WritableNativeMap
import com.flowfoundation.wallet.firebase.auth.getFirebaseJwt
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.AccountType
import com.flowfoundation.wallet.manager.account.getFlowAddress
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.walletdata.FlowWallet
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
import org.onflow.flow.waitForCreatedAccountAddress
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

    /**
     * Register Secure Enclave account - returns early with txId
     * RN will monitor the tx status and call initSecureEnclaveWallet when sealed
     */
    fun registerSecureTypeAccount(username: String, promise: Promise, sendEvent: (String, WritableMap?) -> Unit) {
        logd(TAG, "registerSecureTypeAccount() called - Registering Secure Type Account (Secure Enclave)")
        logd(TAG, "registerSecureTypeAccount() - username: $username")
        ioScope {
            try {
                logd(TAG, "registerSecureTypeAccount() - starting early return registration...")

                // Use early return registration - returns with txId without waiting for tx to seal
                // RN will monitor the tx and call initSecureEnclaveWallet when sealed
                val result = com.flowfoundation.wallet.network.registerOutblockEarlyReturn(username)

                if (result.success && result.txId != null) {
                    logd(TAG, "registerSecureTypeAccount() - registration initiated, txId: ${result.txId}")

                    val response = WritableNativeMap()
                    response.putBoolean("success", true)
                    response.putNull("address") // Address not yet available - tx not sealed
                    response.putString("username", result.username ?: username)
                    response.putString("profileType", "hardware")
                    response.putString("txId", result.txId)
                    response.putNull("error")

                    uiScope {
                        promise.resolve(response)
                    }
                } else {
                    loge(TAG, "registerSecureTypeAccount() - registration failed: ${result.error}")

                    val response = WritableNativeMap()
                    response.putBoolean("success", false)
                    response.putNull("address")
                    response.putNull("username")
                    response.putString("profileType", "hardware")
                    response.putNull("txId")
                    response.putString("error", result.error ?: "Failed to register secure type account")

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
                response.putString("profileType", "hardware")
                response.putNull("txId")
                response.putString("error", e.message ?: "Unknown error")

                uiScope {
                    promise.resolve(response)
                }
            }
        }
    }

    /**
     * Initialize Secure Enclave wallet after transaction has sealed
     * Called by RN after monitoring tx status confirms the transaction is sealed
     */
    fun initSecureEnclaveWallet(txId: String, promise: Promise) {
        logd(TAG, "initSecureEnclaveWallet() called - txId: $txId")
        ioScope {
            try {
                val (success, address) = com.flowfoundation.wallet.network.initWalletWithTxId(txId)

                val response = WritableNativeMap()
                response.putBoolean("success", success)
                response.putString("address", address)
                response.putString("error", if (!success) "Failed to initialize wallet" else null)

                uiScope {
                    promise.resolve(response)
                }
            } catch (e: Exception) {
                loge(TAG, "initSecureEnclaveWallet() - error: ${e.message}")
                e.printStackTrace()

                val response = WritableNativeMap()
                response.putBoolean("success", false)
                response.putNull("address")
                response.putString("error", e.message ?: "Unknown error")

                uiScope {
                    promise.resolve(response)
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

    fun signInWithCustomToken(customToken: String, promise: Promise) {
        logd(TAG, "signInWithCustomToken() called")
        ioScope {
            try {
                com.flowfoundation.wallet.firebase.auth.firebaseCustomLogin(customToken) { isSuccessful, exception ->
                    if (isSuccessful) {
                        logd(TAG, "signInWithCustomToken() - Custom token authentication successful, waiting for JWT...")
                        // Wait for JWT to be available after sign-in
                        // This prevents race conditions where API calls happen before token propagates
                        ioScope {
                            var tokenReady = false
                            var backendValidated = false
                            var attempts = 0
                            val maxAttempts = 15
                            
                            while (!tokenReady && attempts < maxAttempts) {
                                attempts++
                                try {
                                    val jwt = com.flowfoundation.wallet.firebase.auth.getFirebaseJwt(forceRefresh = true)
                                    val firebaseUid = com.flowfoundation.wallet.firebase.auth.firebaseUid()
                                    
                                    if (!jwt.isNullOrBlank() && firebaseUid != null) {
                                        logd(TAG, "signInWithCustomToken() - JWT ready after $attempts attempt(s), Firebase UID: $firebaseUid")
                                        
                                        // Validate with backend - make sure the user is recognized
                                        try {
                                            val service = com.flowfoundation.wallet.network.retrofit()
                                                .create(com.flowfoundation.wallet.network.ApiService::class.java)
                                            val userInfo = service.userInfo().data
                                            logd(TAG, "signInWithCustomToken() - Backend validated, username: ${userInfo.username}")
                                            tokenReady = true
                                            backendValidated = true
                                        } catch (apiError: Exception) {
                                            logd(TAG, "signInWithCustomToken() - Backend validation failed on attempt $attempts: ${apiError.message}")
                                            // Backend might not be ready yet, continue waiting
                                            kotlinx.coroutines.delay(500)
                                        }
                                    } else {
                                        logd(TAG, "signInWithCustomToken() - JWT not ready, attempt $attempts/$maxAttempts")
                                        kotlinx.coroutines.delay(300)
                                    }
                                } catch (e: Exception) {
                                    logd(TAG, "signInWithCustomToken() - JWT check error on attempt $attempts: ${e.message}")
                                    kotlinx.coroutines.delay(300)
                                }
                            }
                            
                            if (tokenReady && backendValidated) {
                                uiScope {
                                    promise.resolve(null)
                                }
                            } else {
                                loge(TAG, "signInWithCustomToken() - Auth not ready after $maxAttempts attempts (tokenReady=$tokenReady, backendValidated=$backendValidated)")
                                uiScope {
                                    promise.reject("CUSTOM_TOKEN_AUTH_ERROR", "Authentication succeeded but backend validation failed", null)
                                }
                            }
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
        logd(TAG, "saveMnemonic() - username: $username, txId: $txId (Flow address creation handled by React Native)")

        ioScope {
            try {
                // Send progress: 0% - Starting
                sendProgressEvent(sendEvent, 0, "Creating account")

                // Clear WalletManager state before adding new account
                WalletManager.clear()
                logd(TAG, "saveMnemonic() - Cleared WalletManager state before adding new account")

                // Step 8: Securely store the mnemonic
                val prefix = storeMnemonicSecurely(mnemonic)

                // Step 9: Authenticate with Firebase
                authenticateWithFirebase(
                    customToken = customToken,
                    onSuccess = {
                        ioScope {
                            try {
                                // Force Firebase ID token refresh to get the new account's JWT
                                // This ensures API requests use the new account's credentials
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
                                val seedPhraseKey = initializeWalletKit(mnemonic, prefix)

                                // Fetch user info from backend
                                val service = com.flowfoundation.wallet.network.retrofit()
                                    .create(com.flowfoundation.wallet.network.ApiService::class.java)
                                val userInfoResponse = service.userInfo()
                                val userInfo = userInfoResponse.data

                                // Flow account creation is now handled by React Native layer
                                // React Native calls profileService().createFlowAddressAndWait() before saveMnemonic()
                                logd(TAG, "saveMnemonic() - Skipping Flow address creation (handled by React Native)")

                                // Send progress: 50% - Mnemonic saved, proceeding with account setup
                                sendProgressEvent(sendEvent, 50, "Setting up account")

                                // Fetch wallet list to get wallet metadata (username, etc.)
                                logd(TAG, "saveMnemonic() - Fetching wallet metadata from backend...")
                                val walletListData: com.flowfoundation.wallet.network.model.WalletListData?
                                try {
                                    walletListData = service.getWalletList().data
                                    if (walletListData == null) {
                                        throw IllegalStateException("Failed to fetch wallet list from backend")
                                    }
                                } catch (e: Exception) {
                                    loge(TAG, "saveMnemonic() - Error fetching wallet list: ${e.message}")
                                    throw e
                                }

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

                                // Account discovery is now handled by React Native layer
                                // React Native will handle wallet initialization after Flow address is created
                                logd(TAG, "saveMnemonic() - Skipping account discovery (handled by React Native)")

                                // Setup AccountManager and WalletManager
                                // Use userInfoWithOriginalUsername to preserve proper capitalization
                                val cryptoProvider = setupAccountAndWallet(prefix, userInfoWithOriginalUsername, walletListData)

                                // Close the drawer to show the updated account in the main view
                                com.flowfoundation.wallet.page.main.MainActivity.getInstance()?.closeDrawer()
                                logd(TAG, "saveMnemonic() - Closed drawer to show updated account")

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
                                    
                                    // Update walletNodes with FlowWallet now that we have the address
                                    // This is needed because walletListData.blockchain may be null during initial setup
                                    val hasFlowWallet = finalAccount.walletNodes.any { it is FlowWallet }
                                    if (!hasFlowWallet) {
                                        logd(TAG, "saveMnemonic() - Adding FlowWallet to walletNodes")
                                        val emojiInfo = AccountEmojiManager.getEmojiByAddress(finalFlowAddress)
                                        val flowWallet = FlowWallet(
                                            address = finalFlowAddress,
                                            name = emojiInfo.emojiName,
                                            emojiId = emojiInfo.emojiId,
                                            chainIdString = currentNetwork,
                                            linkedWallets = emptyList()
                                        )
                                        val updatedNodes = finalAccount.walletNodes + flowWallet
                                        AccountManager.updateCurrentAccount { it.copy(walletNodes = updatedNodes) }
                                        logd(TAG, "saveMnemonic() - FlowWallet added to walletNodes: $finalFlowAddress")
                                    }
                                }

                                // Step 12: Close React Native view (handled by caller)
                                // Step 13: Notification permission (handled by caller)

                                // Progress was already sent to 100% after blockchain confirmation
                                // Small delay to ensure all operations complete before promise resolves
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

        // Clear WalletManager state before adding new account
        WalletManager.clear()
        logd(TAG, "setupAccountAndWallet() - Cleared WalletManager state")

        // Log wallet data structure for debugging
        logd(TAG, "setupAccountAndWallet() - WalletListData: wallets count=${walletListData.wallets?.size}")
        walletListData.wallets?.forEachIndexed { idx, wallet ->
            logd(TAG, "setupAccountAndWallet() -   Wallet $idx: name=${wallet.name}, blockchain count=${wallet.blockchain?.size}")
            wallet.blockchain?.forEach { blockchain ->
                logd(TAG, "setupAccountAndWallet() -     Blockchain: chainId=${blockchain.chainId}, address=${blockchain.address}")
            }
        }

        // Build initial walletNodes with any FlowWallets we know about from the API
        val currentNetwork = chainNetWorkString()
        val initialWalletNodes = walletListData.wallets
            ?.flatMap { walletData ->
                walletData.blockchain
                    ?.filter { it.address.isNotBlank() }
                    ?.map { blockchain ->
                        val formattedAddress = if (blockchain.address.startsWith("0x")) {
                            blockchain.address
                        } else {
                            "0x${blockchain.address}"
                        }
                        val emojiInfo = AccountEmojiManager.getEmojiByAddress(formattedAddress)
                        FlowWallet(
                            address = formattedAddress,
                            name = emojiInfo.emojiName,
                            emojiId = emojiInfo.emojiId,
                            chainIdString = blockchain.chainId.ifBlank { currentNetwork },
                            linkedWallets = emptyList()
                        )
                    }.orEmpty()
            }.orEmpty()

        logd(TAG, "setupAccountAndWallet() - Created ${initialWalletNodes.size} initial FlowWallet nodes")

        // Add account to AccountManager with walletNodes populated
        // profileType = "full" for Recovery Phrase accounts (can derive EOA)
        AccountManager.add(
            Account(
                userInfo = userInfo,
                prefix = prefix,
                wallet = walletListData,
                walletNodes = initialWalletNodes,
                profileType = AccountType.FULL
            ),
            com.flowfoundation.wallet.firebase.auth.firebaseUid()
        )
        logd(TAG, "setupAccountAndWallet() - Account added to AccountManager with FlowWallets in walletNodes (profileType=full)")

        // Select Flow address from wallet data
        val flowAddr = walletListData.wallets
            ?.firstOrNull { wallet -> wallet.blockchain?.any { it.address.isNotBlank() } == true }
            ?.blockchain?.firstOrNull()?.address

        if (!flowAddr.isNullOrBlank()) {
            val formattedAddr = if (flowAddr.startsWith("0x")) flowAddr else "0x$flowAddr"
            WalletManager.selectWalletAddress(formattedAddr)
            logd(TAG, "setupAccountAndWallet() - Selected Flow address: $formattedAddr")
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
            sendEvent("accountCreationProgress", params)
            logd(TAG, "Successfully sent progress event: $progress%")
        } catch (e: Exception) {
            loge(TAG, "Failed to send progress event $progress%: ${e.message}")
            e.printStackTrace()
        }
    }

}
