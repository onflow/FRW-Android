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
                
                // Wait for account to be available in AccountManager with Flow address populated
                // After saveMnemonic, the account exists but Flow address might not be set yet
                var accountRetries = 0
                val maxAccountRetries = 20
                val accountRetryDelayMs = 300L
                val currentNetwork = com.flowfoundation.wallet.manager.app.chainNetWorkString()
                
                var finalAccount: com.flowfoundation.wallet.manager.account.Account? = null
                var finalAccountAddress: String? = null
                
                while (accountRetries < maxAccountRetries) {
                    val accountList = com.flowfoundation.wallet.manager.account.AccountManager.list()
                    
                    // Find account with Flow address populated
                    // If finalAddress is available, use it; otherwise find any account with Flow address
                    val account = if (!finalAddress.isNullOrBlank()) {
                        accountList.find { acc ->
                            val accAddressString = acc.getFlowAddress(currentNetwork, TAG)
                            accAddressString != null && accAddressString.lowercase() == finalAddress.lowercase()
                        }
                    } else {
                        // Find the first account that has a Flow address (should be the newly created one)
                        accountList.firstOrNull { acc ->
                            val accAddressString = acc.getFlowAddress(currentNetwork, TAG)
                            accAddressString != null && accAddressString.isNotBlank()
                        }
                    }
                    
                    if (account != null) {
                        val accAddressString = account.getFlowAddress(currentNetwork, TAG)
                        if (accAddressString != null && accAddressString.isNotBlank()) {
                            finalAccount = account
                            finalAccountAddress = accAddressString
                            logd(TAG, "createLinkedCOAAccount() - Account found with Flow address (attempt ${accountRetries + 1})")
                            logd(TAG, "createLinkedCOAAccount() - Account username: ${account.userInfo.username}, prefix: ${account.prefix}, address: $accAddressString")
                            break
                        }
                    }
                    
                    accountRetries++
                    if (accountRetries < maxAccountRetries) {
                        logd(TAG, "createLinkedCOAAccount() - Waiting for account Flow address... (attempt $accountRetries/$maxAccountRetries)")
                        logd(TAG, "createLinkedCOAAccount() - AccountManager.list() size: ${accountList.size}")
                        accountList.forEach { acc ->
                            val accAddressString = acc.getFlowAddress(currentNetwork, TAG)
                            logd(TAG, "createLinkedCOAAccount() - Account: username=${acc.userInfo.username}, address=$accAddressString")
                        }
                        kotlinx.coroutines.delay(accountRetryDelayMs)
                    }
                }
                
                if (finalAccount == null || finalAccountAddress == null) {
                    val accountList = com.flowfoundation.wallet.manager.account.AccountManager.list()
                    loge(TAG, "createLinkedCOAAccount() - Account with Flow address not found after $maxAccountRetries attempts")
                    loge(TAG, "createLinkedCOAAccount() - AccountManager.list() size: ${accountList.size}")
                    accountList.forEach { acc ->
                        val accAddressString = acc.getFlowAddress(currentNetwork, TAG)
                        logd(TAG, "createLinkedCOAAccount() - Account in list: username=${acc.userInfo.username}, address=$accAddressString")
                    }
                    uiScope {
                        promise.reject("COA_CREATION_ERROR", "Account Flow address not available: cannot create COA account. Found accounts: ${accountList.size}")
                    }
                    return@ioScope
                }
                
                logd(TAG, "createLinkedCOAAccount() - Account verified with Flow address: $finalAccountAddress, proceeding with COA creation...")
                
                // Execute Cadence transaction to create linked COA account
                val txId = try {
                    com.flowfoundation.wallet.manager.flowjvm.cadenceCreateCOAAccount()
                } catch (e: Exception) {
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

    fun saveMnemonic(mnemonic: String, customToken: String, txId: String, promise: Promise) {
        logd(TAG, "saveMnemonic() called - EOA account initialization")
        logd(TAG, "saveMnemonic() - txId: $txId")

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

                                // Step 11: Fast account discovery using txId
                                discoverAccountFast(seedPhraseKey, txId, walletListData)

                                // Setup AccountManager and WalletManager
                                val cryptoProvider = setupAccountAndWallet(prefix, userInfo, walletListData)

                                // Mark user as registered so app knows they've completed onboarding
                                com.flowfoundation.wallet.utils.setRegistered()
                                logd(TAG, "saveMnemonic() - User marked as registered")

                                // Track account creation
                                trackAccountCreation(cryptoProvider)

                                logd(TAG, "saveMnemonic() - EOA account initialization complete!")
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
                                            }
                                        } catch (e: Exception) {
                    logd(TAG, "discoverAccountFast() - Warning: Could not fetch account: ${e.message}")
                }
            }
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
