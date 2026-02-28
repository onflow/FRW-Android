package com.flowfoundation.wallet.reactnative.bridge.handlers

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.WritableNativeMap
import com.flow.wallet.CryptoProvider
import com.flow.wallet.crypto.BIP39
import com.flow.wallet.wallet.WalletFactory
import com.flowfoundation.wallet.firebase.auth.getFirebaseJwt
import org.onflow.flow.ChainId
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.AccountWalletManager
import com.flowfoundation.wallet.manager.account.firstFlowWalletAddress
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.key.storage.KeyStorageManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.walletdata.EOAWallet
import com.flowfoundation.wallet.manager.walletdata.FlowWallet
import com.flowfoundation.wallet.manager.walletdata.MainWallet
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
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.storage.InMemoryStorage
import com.flowfoundation.wallet.firebase.auth.firebaseUid
import com.flowfoundation.wallet.firebase.auth.isAnonymousSignIn
import com.flowfoundation.wallet.firebase.auth.signInAnonymously
import com.flowfoundation.wallet.wallet.DERIVATION_PATH
import org.onflow.flow.models.toHexString
import org.onflow.flow.models.DomainTag
import org.onflow.flow.models.SigningAlgorithm
import org.onflow.flow.models.HashingAlgorithm

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
                val inMemoryStorage = InMemoryStorage()

                // Use Flow derivation path: m/44'/539'/0'/0/0
                val derivationPath = DERIVATION_PATH

                val seedPhraseKey = SeedPhraseKey(
                    mnemonicString = mnemonic,
                    passphrase = "",
                    derivationPath = derivationPath,
                    storage = inMemoryStorage
                )

                // Derive public key using ECDSA_secp256k1 (matches EOA flow default)
                val publicKeyBytes = seedPhraseKey.publicKey(SigningAlgorithm.ECDSA_secp256k1)
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
                    hashAlgoStr = HashingAlgorithm.SHA2_256.value,
                    signAlgoStr = SigningAlgorithm.ECDSA_secp256k1.value,
                    weight = 1000, // Standard weight for Flow accounts
                    hashAlgo = HashingAlgorithm.SHA2_256.cadenceIndex, // SHA2_256
                    signAlgo = SigningAlgorithm.ECDSA_secp256k1.cadenceIndex  // ECDSA_secp256k1
                )

                // Derive EVM address from the seed phrase for faster display in UI
                // Uses BIP44 path m/44'/60'/0'/0/0 (Ethereum standard)
                val evmAddress: String? = try {
                    val wallet = WalletFactory.createKeyWallet(
                        seedPhraseKey,
                        setOf(ChainId.Mainnet, ChainId.Testnet),
                        inMemoryStorage
                    )
                    val address = wallet.ethAddress(0)
                    logd(TAG, "generateSeedPhrase() - Derived EVM address: ${address.take(10)}...")
                    address
                } catch (e: Exception) {
                    logw(TAG, "generateSeedPhrase() - Failed to derive EVM address: ${e.message}")
                    null
                }

                // Create SPResponse
                val response = RNBridge.SPResponse(
                    mnemonic = mnemonic,
                    accountKey = accountKey,
                    drivepath = derivationPath,
                    evmAddress = evmAddress
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

    /**
     * Get all signatures needed for v4 API registration
     * Signs in anonymously to Firebase, gets JWT, and signs it with both Flow and EVM keys derived from mnemonic
     * @param mnemonic - The recovery phrase to derive signing keys from
     * @returns Promise with flowSignature, evmSignature, and eoaAddress
     */
    fun getV4RegistrationSignatures(mnemonic: String, promise: Promise) {
        logd(TAG, "getV4RegistrationSignatures() called")
        ioScope {
            try {
                // Step 1: Sign in anonymously to Firebase if not already signed in
                if (!isAnonymousSignIn()) {
                    logd(TAG, "getV4RegistrationSignatures() - Signing in anonymously...")
                    Firebase.auth.signOut()
                    val signedIn = signInAnonymously()
                    if (!signedIn) {
                        throw IllegalStateException("Failed to sign in anonymously to Firebase")
                    }
                    logd(TAG, "getV4RegistrationSignatures() - Signed in anonymously")
                }

                // Step 2: Get Firebase JWT
                val firebaseJwt = getFirebaseJwt()
                if (firebaseJwt.isBlank()) {
                    throw IllegalStateException("Failed to get Firebase JWT")
                }
                logd(TAG, "getV4RegistrationSignatures() - Got Firebase JWT")

                // Step 3: Derive private key from mnemonic
                val inMemoryStorage = InMemoryStorage()
                val derivationPath = DERIVATION_PATH
                val seedPhraseKey = SeedPhraseKey(
                    mnemonicString = mnemonic,
                    passphrase = "",
                    derivationPath = derivationPath,
                    storage = inMemoryStorage
                )

                // Step 4: Sign the Firebase JWT with the derived key (Flow signature)
                // Important: Prepend DomainTag.User.bytes to match what backend expects
                val dataToSign = DomainTag.User.bytes + firebaseJwt.toByteArray(Charsets.UTF_8)
                logd(TAG, "getV4RegistrationSignatures() - Signing data with DomainTag.User prefix, total size: ${dataToSign.size}")

                val flowSignatureBytes = seedPhraseKey.sign(
                    dataToSign,
                    SigningAlgorithm.ECDSA_secp256k1,
                    HashingAlgorithm.SHA2_256
                )

                val flowSignature = flowSignatureBytes.joinToString("") { "%02x".format(it) }
                logd(TAG, "getV4RegistrationSignatures() - Generated Flow signature, length: ${flowSignature.length} chars (${flowSignatureBytes.size} bytes)")

                // Step 5: Derive EVM address and signature
                // IMPORTANT: EVM must use Ethereum BIP44 path m/44'/60'/0'/0/0, NOT Flow path
                // Use Trust Wallet Core's HDWallet for proper derivation
                val hdWallet = wallet.core.jni.HDWallet(mnemonic, "")
                val evmDerivationPath = "m/44'/60'/0'/0/0" // Standard Ethereum BIP44 path

                // Get private key for EVM using secp256k1 curve with Ethereum path
                val evmPrivateKey = hdWallet.getKeyByCurve(wallet.core.jni.Curve.SECP256K1, evmDerivationPath)
                val evmPublicKey = evmPrivateKey.getPublicKeySecp256k1(false) // uncompressed

                // Derive EVM address from public key using Trust Wallet Core
                val eoaAddress = wallet.core.jni.AnyAddress(evmPublicKey, wallet.core.jni.CoinType.ETHEREUM).description()
                logd(TAG, "getV4RegistrationSignatures() - Derived EOA address from mnemonic: $eoaAddress")

                // Sign keccak256(idToken) for EVM - NO domain tag, same as extension
                val jwtBytes = firebaseJwt.toByteArray(Charsets.UTF_8)
                val jwtHash = wallet.core.jni.Hash.keccak256(jwtBytes)
                logd(TAG, "getV4RegistrationSignatures() - EVM: keccak256 hash of JWT, hash size: ${jwtHash.size}")

                // Sign the digest with secp256k1
                val signatureData = evmPrivateKey.sign(jwtHash, wallet.core.jni.Curve.SECP256K1)

                val evmSignature = "0x" + signatureData.joinToString("") { "%02x".format(it) }
                logd(TAG, "getV4RegistrationSignatures() - Generated EVM signature from mnemonic, length: ${evmSignature.length} chars")

                // Return both Flow and EVM signatures (matching RN interface)
                val result = WritableNativeMap()
                result.putString("flowSignature", flowSignature)
                result.putString("evmSignature", evmSignature)
                result.putString("eoaAddress", eoaAddress)

                uiScope {
                    promise.resolve(result)
                }
            } catch (e: Exception) {
                loge(TAG, "getV4RegistrationSignatures() - error: ${e.message}")
                e.printStackTrace()
                uiScope {
                    promise.reject("GET_V4_REGISTRATION_SIGNATURES_ERROR", "Failed to get v4 registration signatures: ${e.message}", e)
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
                                    val jwt = getFirebaseJwt(forceRefresh = true)
                                    val firebaseUid = com.flowfoundation.wallet.firebase.auth.firebaseUid()

                                    if (jwt.isNotBlank() && firebaseUid != null) {
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

    fun saveMnemonic(mnemonic: String, customToken: String, txId: String, username: String, evmAddress: String?, promise: Promise, sendEvent: (String, WritableMap?) -> Unit) {
        logd(TAG, "saveMnemonic() called - Seed phrase account initialization (cleaner architecture)")
        logd(TAG, "saveMnemonic() - username: $username, txId: $txId, evmAddress: ${evmAddress?.take(10) ?: "null"} (Flow address creation handled by React Native)")

        ioScope {
            try {
                // Send progress: 0% - Starting
                sendProgressEvent(sendEvent, 0, "Creating account")

                // Clear WalletManager state before adding new account
                WalletManager.clear()
                logd(TAG, "saveMnemonic() - Cleared WalletManager state before adding new account")

                // Note: Mnemonic will be stored AFTER we get the userId from the backend
                // This is the cleaner architecture - mnemonic-only, no prefix-based key duplication

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
                                        val jwt = getFirebaseJwt(forceRefresh = true)
                                        val currentUid = com.flowfoundation.wallet.firebase.auth.firebaseUid()

                                        if (jwt.isNotBlank() && currentUid != null) {
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
                                val walletListData: WalletListData?
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

                                // Get userId from wallet data for mnemonic storage
                                val userId = walletListData.id
                                if (userId.isNullOrBlank()) {
                                    throw IllegalStateException("Wallet ID is null or blank - cannot store mnemonic")
                                }
                                logd(TAG, "saveMnemonic() - Got userId for mnemonic storage: $userId")

                                // Store mnemonic using AccountWalletManager (cleaner architecture - no prefix duplication)
                                val mnemonicStored = AccountWalletManager.storeHDWalletMnemonic(userId, mnemonic)
                                if (!mnemonicStored) {
                                    throw IllegalStateException("Failed to store mnemonic for userId: $userId")
                                }
                                logd(TAG, "saveMnemonic() - Mnemonic stored successfully via AccountWalletManager")
                                // Also persist in independent key storage so it survives account-cache loss
                                KeyStorageManager.saveSeedPhrase(userId, mnemonic)

                                // Account discovery is now handled by React Native layer
                                // React Native will handle wallet initialization after Flow address is created
                                logd(TAG, "saveMnemonic() - Skipping account discovery (handled by React Native)")

                                // Setup AccountManager and WalletManager (no prefix - mnemonic only)
                                // Use userInfoWithOriginalUsername to preserve proper capitalization
                                // Pass evmAddress to create EOA wallet immediately
                                val cryptoProvider = setupAccountAndWalletMnemonicOnly(userId, userInfoWithOriginalUsername, walletListData, evmAddress)

                                // Close the drawer to show the updated account in the main view
                                com.flowfoundation.wallet.page.main.MainActivity.getInstance()?.closeDrawer()
                                logd(TAG, "saveMnemonic() - Closed drawer to show updated account")

                                // Mark user as registered so app knows they've completed onboarding
                                com.flowfoundation.wallet.utils.setRegistered()
                                logd(TAG, "saveMnemonic() - User marked as registered")

                                // Track account creation
                                trackAccountCreation(cryptoProvider)

                                logd(TAG, "saveMnemonic() - Seed phrase account initialization complete!")

                                // Wait for wallet info to be populated with Flow address
                                // This ensures the account is ready for COA creation
                                logd(TAG, "saveMnemonic() - Waiting for wallet info to be populated...")
                                var waitRetries = 0
                                val maxWaitRetries = 30 // 15 seconds max
                                val currentNetwork = chainNetWorkString()

                                while (waitRetries < maxWaitRetries) {
                                    val currentAccount = AccountManager.get()
                                    val flowAddress = currentAccount?.firstFlowWalletAddress()

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
                                val finalFlowAddress: String? = finalAccount?.firstFlowWalletAddress()
                                if (finalFlowAddress.isNullOrBlank()) {
                                    logw(TAG, "saveMnemonic() - Flow address not populated after waiting, but continuing anyway")
                                } else {
                                    logd(TAG, "saveMnemonic() - Account ready with Flow address: $finalFlowAddress")

                                    // Update walletNodes with FlowWallet now that we have the address
                                    // This is needed because walletListData.blockchain may be null during initial setup
                                    val hasFlowWallet = finalAccount.walletNodes.any { it is FlowWallet }
                                    if (!hasFlowWallet) {
                                        logd(TAG, "saveMnemonic() - Adding FlowWallet to walletNodes")
                                        val address: String = finalFlowAddress
                                        val emojiInfo = AccountEmojiManager.getEmojiByAddress(address)
                                        val flowWallet = FlowWallet(
                                            address = address,
                                            name = emojiInfo.emojiName,
                                            emojiId = emojiInfo.emojiId,
                                            chainIdString = currentNetwork,
                                            linkedWallets = emptyList()
                                        )
                                        val updatedNodes = finalAccount.walletNodes + flowWallet
                                        AccountManager.updateCurrentAccount { it.copy(walletNodes = updatedNodes) }
                                        logd(TAG, "saveMnemonic() - FlowWallet added to walletNodes: $address")
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

    // Note: storeMnemonicSecurely has been removed in favor of AccountWalletManager.storeHDWalletMnemonic()
    // This is the cleaner architecture - mnemonic-only storage, no prefix-based key duplication


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

    // Note: initializeWalletKit has been removed in favor of the cleaner architecture
    // RN seed phrase accounts now only store the mnemonic (via AccountWalletManager)
    // and don't store a duplicate prefix-based private key

    /**
     * Setup account and wallet for mnemonic-only accounts (cleaner architecture).
     *
     * This is used for RN seed phrase accounts that only store the mnemonic,
     * without a prefix-based private key duplication. The account will have:
     * - No prefix field set (null)
     * - Mnemonic stored via AccountWalletManager (keyed by userId)
     *
     * CryptoProviderManager and WalletCreationHelper will detect this as a mnemonic-only
     * account and use AccountWalletManager.getHDWalletMnemonicByUID() to access the key.
     */
    private fun setupAccountAndWalletMnemonicOnly(
        userId: String,
        userInfo: UserInfoData,
        walletListData: WalletListData,
        evmAddress: String? = null
    ): CryptoProvider {
        logd(TAG, "setupAccountAndWalletMnemonicOnly() - Setting up mnemonic-only account...")

        // Clear WalletManager state before adding new account
        WalletManager.clear()
        logd(TAG, "setupAccountAndWalletMnemonicOnly() - Cleared WalletManager state")

        // Log wallet data structure for debugging
        logd(TAG, "setupAccountAndWalletMnemonicOnly() - WalletListData: wallets count=${walletListData.wallets?.size}")
        walletListData.wallets?.forEachIndexed { idx, wallet ->
            logd(TAG, "setupAccountAndWalletMnemonicOnly() -   Wallet $idx: name=${wallet.name}, blockchain count=${wallet.blockchain?.size}")
            wallet.blockchain?.forEach { blockchain ->
                logd(TAG, "setupAccountAndWalletMnemonicOnly() -     Blockchain: chainId=${blockchain.chainId}, address=${blockchain.address}")
            }
        }

        // Build initial walletNodes with any FlowWallets we know about from the API
        val currentNetwork = chainNetWorkString()
        val flowWalletNodes = walletListData.wallets
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

        // Build wallet nodes list starting with Flow wallets
        val initialWalletNodes = flowWalletNodes.toMutableList<MainWallet>()

        // Add EOA wallet if evmAddress is provided (pre-derived from seed phrase)
        if (!evmAddress.isNullOrBlank()) {
            val formattedEvmAddress = if (evmAddress.startsWith("0x")) evmAddress else "0x$evmAddress"
            val eoaEmojiInfo = AccountEmojiManager.getEmojiByAddress(formattedEvmAddress)
            val eoaWallet = EOAWallet(
                address = formattedEvmAddress,
                name = eoaEmojiInfo.emojiName,
                emojiId = eoaEmojiInfo.emojiId
            )
            initialWalletNodes.add(eoaWallet)
            logd(TAG, "setupAccountAndWalletMnemonicOnly() - Added pre-derived EOA wallet: $formattedEvmAddress")
        }

        logd(TAG, "setupAccountAndWalletMnemonicOnly() - Created ${initialWalletNodes.size} initial wallet nodes (${flowWalletNodes.size} Flow + ${if (evmAddress != null) 1 else 0} EOA)")

        // Add account to AccountManager WITHOUT prefix (mnemonic-only architecture)
        // The account.wallet.id will be used to look up the mnemonic via AccountWalletManager
        AccountManager.add(
            Account(
                userInfo = userInfo,
                prefix = null, // No prefix - mnemonic-only account
                wallet = walletListData,
                walletNodes = initialWalletNodes
            ),
            com.flowfoundation.wallet.firebase.auth.firebaseUid()
        )
        logd(TAG, "setupAccountAndWalletMnemonicOnly() - Account added to AccountManager (mnemonic-only, no prefix)")

        // Select Flow address from wallet data
        val flowAddr = walletListData.wallets
            ?.firstOrNull { wallet -> wallet.blockchain?.any { it.address.isNotBlank() } == true }
            ?.blockchain?.firstOrNull()?.address

        if (!flowAddr.isNullOrBlank()) {
            val formattedAddr = if (flowAddr.startsWith("0x")) flowAddr else "0x$flowAddr"
            WalletManager.selectWalletAddress(formattedAddr)
            logd(TAG, "setupAccountAndWalletMnemonicOnly() - Selected Flow address: $formattedAddr")
        }

        // Relaunch MainActivity to ensure all state is completely fresh
        uiScope {
            com.flowfoundation.wallet.page.main.MainActivity.relaunch(
                com.flowfoundation.wallet.utils.Env.getApp(),
                clearTop = true
            )
        }
        logd(TAG, "setupAccountAndWalletMnemonicOnly() - Scheduled MainActivity relaunch for fresh state")

        // Get crypto provider for the current account (mnemonic-based)
        val currentAccount = AccountManager.get()
            ?: throw IllegalStateException("Account not found after adding to AccountManager")

        val cryptoProvider = CryptoProviderManager.generateAccountCryptoProvider(currentAccount)
            ?: throw IllegalStateException("Failed to generate crypto provider")

        logd(TAG, "setupAccountAndWalletMnemonicOnly() - Crypto provider generated successfully")

        return cryptoProvider
    }

    // Legacy function for prefix-based accounts (kept for backward compatibility)
    private fun setupAccountAndWallet(
        prefix: String,
        userInfo: UserInfoData,
        walletListData: WalletListData,
        evmAddress: String? = null
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
        val flowWalletNodes = walletListData.wallets
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

        // Build wallet nodes list starting with Flow wallets
        val initialWalletNodes = flowWalletNodes.toMutableList<MainWallet>()

        // Add EOA wallet if evmAddress is provided (pre-derived from seed phrase)
        if (!evmAddress.isNullOrBlank()) {
            val formattedEvmAddress = if (evmAddress.startsWith("0x")) evmAddress else "0x$evmAddress"
            val eoaEmojiInfo = AccountEmojiManager.getEmojiByAddress(formattedEvmAddress)
            val eoaWallet = EOAWallet(
                address = formattedEvmAddress,
                name = eoaEmojiInfo.emojiName,
                emojiId = eoaEmojiInfo.emojiId
            )
            initialWalletNodes.add(eoaWallet)
            logd(TAG, "setupAccountAndWallet() - Added pre-derived EOA wallet: $formattedEvmAddress")
        }

        logd(TAG, "setupAccountAndWallet() - Created ${initialWalletNodes.size} initial wallet nodes (${flowWalletNodes.size} Flow + ${if (evmAddress != null) 1 else 0} EOA)")

        // Add account to AccountManager with walletNodes populated
        AccountManager.add(
            Account(
                userInfo = userInfo,
                prefix = prefix,
                wallet = walletListData,
                walletNodes = initialWalletNodes
            ),
            com.flowfoundation.wallet.firebase.auth.firebaseUid()
        )
        logd(TAG, "setupAccountAndWallet() - Account added to AccountManager with FlowWallets in walletNodes")

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
        uiScope {
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
