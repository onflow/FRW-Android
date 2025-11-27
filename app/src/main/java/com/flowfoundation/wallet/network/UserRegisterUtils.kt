package com.flowfoundation.wallet.network

import android.webkit.WebStorage
import android.widget.Toast
import com.flow.wallet.crypto.BIP39
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.storage.FileSystemStorage
import com.flow.wallet.wallet.WalletFactory
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.firebase.auth.firebaseCustomLogin
import com.flowfoundation.wallet.firebase.auth.firebaseUid
import com.flowfoundation.wallet.firebase.auth.getFirebaseJwt
import com.flowfoundation.wallet.firebase.auth.isAnonymousSignIn
import com.flowfoundation.wallet.firebase.auth.signInAnonymously
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.evm.DAppEVMConnectionManager
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.key.KeyCompatibilityManager
import com.flowfoundation.wallet.manager.nft.NftCollectionStateManager
import com.flowfoundation.wallet.manager.staking.StakingManager
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.mixpanel.AccountCreateKeyType
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.network.model.AccountKey
import com.flowfoundation.wallet.network.model.LoginRequest
import com.flowfoundation.wallet.network.model.RegisterRequest
import com.flowfoundation.wallet.network.model.RegisterResponse
import com.flowfoundation.wallet.page.walletrestore.firebaseLogin
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.cleanBackupMnemonicPreference
import com.flowfoundation.wallet.utils.clearCacheDir
import com.flowfoundation.wallet.utils.error.AccountError
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.error.WalletError
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.readWalletPassword
import com.flowfoundation.wallet.utils.setMeowDomainClaimed
import com.flowfoundation.wallet.utils.setRegistered
import com.flowfoundation.wallet.utils.storeWalletPassword
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.wallet.Wallet
import com.flowfoundation.wallet.manager.flow.FlowCadenceApi
import com.flowfoundation.wallet.manager.transaction.isExecuteFinished
import com.flowfoundation.wallet.manager.transaction.isFailed
import com.flowfoundation.wallet.wallet.createWalletFromServer
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import org.onflow.flow.ChainId
import org.onflow.flow.models.SigningAlgorithm
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val TAG = "UserRegisterUtils"

// Register Secure Type/COA account (Secure Enclave profile - hardware-backed keys only)
// This creates a COA account WITHOUT a mnemonic (no seed phrase backup)
// For EOA accounts with seed phrase, use the React Native flow with saveMnemonic()
suspend fun registerOutblock(
    username: String,
) = suspendCoroutine { continuation ->
    ioScope {
        // registerOutblockUserInternal will call registerServer, which creates and stores
        // the primary private key associated with the prefix, and performs the actual
        // server registration using that key's public key.
        registerOutblockUserInternal(username) { isSuccess, prefix, txId ->
            ioScope {
                if (isSuccess) {
                    // At this point, user is registered on server, Firebase is synced,
                    // and the correct private key (from registerServer) is stored with the prefix.

                    // Declare service here for fetching user and wallet info
                    val service = retrofit().create(ApiService::class.java)

                    // Get the transaction ID for the Flow account creation
                    if (txId.isNullOrBlank()) {
                        loge(TAG, "No transaction ID returned from registration")
                        continuation.resume(false)
                        return@ioScope
                    }
                    logd(TAG, "Flow account creation txId: $txId")

                    // Wait for the Flow transaction to complete and get the created address
                    val chainId = when (chainNetWorkString()) {
                        "mainnet" -> ChainId.Mainnet
                        "testnet" -> ChainId.Testnet
                        else -> ChainId.Mainnet
                    }

                    val createdFlowAddress = getCreatedAddressFromTx(txId)
                    if (createdFlowAddress == null) {
                        loge(TAG, "Failed to get Flow address from transaction $txId")
                        continuation.resume(false)
                        return@ioScope
                    }
                    logd(TAG, "Flow account created successfully with address: $createdFlowAddress")

                    // Initialize wallet structure on backend (needed for COA account visibility)
                    // This initializes the wallet record but does NOT create an EOA account

                    // The COA account is created by registerServer() above
                    createWalletFromServer()
                    setRegistered()

                    // Wallet and Account object creation should use data from the successful registration (via registerServer)
                    // The service calls here should ideally just fetch the latest state if needed,
                    // not perform new registrations or key creations.

                    // Retry fetching user info with exponential backoff
                    // Sometimes the user is created but not immediately available via the API
                    var userInfo: com.flowfoundation.wallet.network.model.UserInfoData? = null
                    var retryCount = 0
                    val maxRetries = 5

                    while (userInfo == null && retryCount < maxRetries) {
                        try {
                            val delayMs = when (retryCount) {
                                0 -> 500L   // First attempt after 500ms
                                1 -> 1000L  // Second attempt after 1s
                                2 -> 2000L  // Third attempt after 2s
                                3 -> 3000L  // Fourth attempt after 3s
                                else -> 5000L // Final attempt after 5s
                            }
                            delay(delayMs)

                            logd(TAG, "Attempting to fetch user info (attempt ${retryCount + 1}/$maxRetries)")
                            userInfo = service.userInfo().data
                            logd(TAG, "Successfully fetched user info on attempt ${retryCount + 1}")
                        } catch (e: Exception) {
                            retryCount++
                            if (retryCount >= maxRetries) {
                                loge(TAG, "Failed to fetch user info after $maxRetries attempts: ${e.message}")
                                continuation.resume(false)
                                return@ioScope
                            }
                            logd(TAG, "Failed to fetch user info (attempt $retryCount/$maxRetries): ${e.message}, retrying...")
                        }
                    }

                    // Retry fetching wallet list with exponential backoff
                    var walletListData: com.flowfoundation.wallet.network.model.WalletListData? = null
                    retryCount = 0

                    while (walletListData == null && retryCount < maxRetries) {
                        try {
                            val delayMs = when (retryCount) {
                                0 -> 500L   // First attempt after 500ms
                                1 -> 1000L  // Second attempt after 1s
                                2 -> 2000L  // Third attempt after 2s
                                3 -> 3000L  // Fourth attempt after 3s
                                else -> 5000L // Final attempt after 5s
                            }
                            delay(delayMs)

                            logd(TAG, "Attempting to fetch wallet list (attempt ${retryCount + 1}/$maxRetries)")
                            val fetchedData = service.getWalletList().data
                            if (fetchedData != null) {
                                walletListData = fetchedData
                                logd(TAG, "Successfully fetched wallet list on attempt ${retryCount + 1}")
                            } else {
                                logd(TAG, "Wallet list data is null on attempt ${retryCount + 1}, retrying...")
                                retryCount++
                            }
                        } catch (e: Exception) {
                            retryCount++
                            if (retryCount >= maxRetries) {
                                loge(TAG, "Failed to fetch wallet list after $maxRetries attempts: ${e.message}")
                                continuation.resume(false)
                                return@ioScope
                            }
                            logd(TAG, "Failed to fetch wallet list (attempt $retryCount/$maxRetries): ${e.message}, retrying...")
                        }
                    }

                    if (walletListData == null) {
                        loge(TAG, "No wallet data found after $maxRetries attempts")
                        continuation.resume(false)
                        return@ioScope
                    }

                    // Log the wallet addresses from backend
                    logd(TAG, "Wallet list data:")
                    logd(TAG, "  - Wallets count: ${walletListData.wallets?.size}")
                    walletListData.wallets?.forEach { wallet ->
                        wallet.blockchain?.forEach { blockchain ->
                            logd(TAG, "  - Blockchain: ${blockchain.chainId}, Address: ${blockchain.address}")
                        }
                    }
                    logd(TAG, "Expected Flow address from transaction: $createdFlowAddress")

                    // Now that we have the wallet data with account address, use fetchAccountByAddress
                    // to populate the wallet SDK with the account details from Flow network
                    val storage = FileSystemStorage(File(Env.getApp().filesDir, "wallet"))
                    val keyForWalletSDK = KeyCompatibilityManager.getPrivateKeyWithFallback(prefix, storage)
                    if (keyForWalletSDK == null) {
                        logd(TAG, "Failed to retrieve stored private key for Wallet SDK init from both new and old storage.")
                        continuation.resume(false)
                        return@ioScope
                    }

                    val walletForSDK = WalletFactory.createKeyWallet(
                        keyForWalletSDK,
                        setOf(ChainId.Mainnet, ChainId.Testnet),
                        storage
                    )

                    // Use fetchAccountByAddress to populate wallet SDK with the created Flow account
                    try {
                        logd(TAG, "Fetching account by address: $createdFlowAddress from Flow network")
                        walletForSDK.fetchAccountByAddress(createdFlowAddress, chainId)
                        logd(TAG, "Successfully populated wallet SDK with Flow account from network")
                    } catch (e: Exception) {
                        loge(TAG, "Failed to fetch Flow account $createdFlowAddress into Wallet SDK: ${e.message}")
                        // Continue anyway - the account exists on-chain even if SDK fetch failed
                    }

                    if (userInfo != null) {
                        AccountManager.add(
                            Account(
                                userInfo = userInfo,
                                prefix = prefix, // This prefix matches the one used to store the key in registerServer
                                wallet = walletListData
                            ),
                            firebaseUid()
                        )
                        logd(TAG, "Account added to AccountManager.")
                    } else {
                        loge(TAG, "Cannot add account - userInfo is null")
                        continuation.resume(false)
                        return@ioScope
                    }

                    // Initialize WalletManager to pick up the new account/wallet state
                    WalletManager.init()

                    // Now, get the CryptoProvider. It should use the prefix and load the key stored by registerServer.
                    val currentAccount = AccountManager.get() // Should be the newly added account
                    if (currentAccount == null || currentAccount.prefix != prefix) {
                        loge(TAG, "Critical: currentAccount after add is null or prefix mismatch!")
                        continuation.resume(false)
                        return@ioScope
                    }

                    val cryptoProvider = CryptoProviderManager.generateAccountCryptoProvider(currentAccount)
                    if (cryptoProvider == null) {
                        loge(TAG, "Failed to generate crypto provider for the registered account.")
                        continuation.resume(false)
                        return@ioScope
                    }
                    logd(TAG, "Crypto provider generated successfully for registered account. Public key: ${cryptoProvider.getPublicKey()}")

                    // The public key from this cryptoProvider SHOULD now match the on-chain key
                    // because both originate from the single private key created and stored in registerServer.

                    MixpanelManager.accountCreated(
                        cryptoProvider.getPublicKey(),
                        AccountCreateKeyType.KEY_STORE, // This might need re-evaluation; it's a prefix-stored key
                        cryptoProvider.getSignatureAlgorithm().value,
                        cryptoProvider.getHashAlgorithm().algorithm
                    )
                    clearUserCache()

                    // Trigger wallet data update to refresh UI (e.g., drawer sidebar)
                    // This ensures the sidebar shows COA with correct EVM badge immediately
                    walletListData?.let { data ->
                        AccountManager.updateWalletInfo(data)
                        logd(TAG, "registerOutblock() - Triggered UI refresh via updateWalletInfo")

                        // Close the drawer to show the updated account in the main view
                        com.flowfoundation.wallet.page.main.MainActivity.getInstance()?.closeDrawer()
                        logd(TAG, "registerOutblock() - Closed drawer to show updated account")
                    }

                    continuation.resume(true)
                } else {
                    // Registration failed in registerOutblockUserInternal (e.g., server or Firebase issue)
                    loge(TAG, "registerOutblock() - registerOutblockUserInternal indicated failure. Check logs above for details.")
                    // resumeAccount() // This was here, consider if it's needed or if failure is handled by caller
                    continuation.resume(false)
                }
            }
        }
    }
}

private suspend fun registerOutblockUserInternal(
    username: String,
    callback: (isSuccess: Boolean, prefix: String, txId: String?) -> Unit,
) {
    val prefix = generatePrefix(username)
    try {
        if (!setToAnonymous()) {
            loge(TAG, "registerOutblockUserInternal() - Failed to set Firebase to anonymous sign-in")
            resumeAccount()
            callback.invoke(false, prefix, null)
            return
        }
        val user = registerServer(username, prefix)

        if (user.status > 400) {
            loge(TAG, "registerOutblockUserInternal() - Server registration failed with status: ${user.status}, message: ${user.message}")
            callback(false, prefix, null)
            return
        }
        logd(TAG, "SYNC Register userId:::${user.data.uid}")
        logd(TAG, "start delete user")
        registerFirebase(user) { isSuccess ->
            if (!isSuccess) {
                loge(TAG, "registerOutblockUserInternal() - Firebase registration failed")
                callback.invoke(false, prefix, null)
                return@registerFirebase
            }

            // After Firebase registration, create the Flow address to get the transaction ID
            // This matches the recovery phrase flow which calls createFlowAddress() (v2 endpoint) separately
            ioScope {
                try {
                    logd(TAG, "Creating Flow address via /v2/user/address endpoint...")
                    val service = retrofit().create(ApiService::class.java)
                    val createFlowAddressResponse = service.createFlowAddress()

                    logd(TAG, "Flow address creation response: $createFlowAddressResponse")
                    logd(TAG, "Response status: ${createFlowAddressResponse.status}")
                    logd(TAG, "Response message: ${createFlowAddressResponse.message}")
                    logd(TAG, "Response data: ${createFlowAddressResponse.data}")

                    val txId = createFlowAddressResponse.data?.txId

                    if (txId.isNullOrBlank()) {
                        loge(TAG, "No transaction ID returned from /v2/user/address endpoint")
                        callback.invoke(false, prefix, null)
                    } else {
                        logd(TAG, "Flow address creation successful, txId: $txId")
                        callback.invoke(true, prefix, txId)
                    }
                } catch (e: Exception) {
                    loge(TAG, "Failed to create Flow address: ${e.message}")
                    loge(TAG, "Stack trace: ${e.stackTraceToString()}")
                    callback.invoke(false, prefix, null)
                }
            }
        }
    } catch (e: Exception) {
        loge(TAG, "registerOutblockUserInternal() - Exception occurred: ${e.message}")
        loge(TAG, "registerOutblockUserInternal() - Stack trace: ${e.stackTraceToString()}")
        if (e is IllegalStateException) {
            ErrorReporter.reportCriticalWithMixpanel(WalletError.KEY_STORE_FAILED, e)
        } else {
            ErrorReporter.reportWithMixpanel(AccountError.REGISTER_USER_FAILED, e)
        }
        callback.invoke(false, prefix, null)
    }
}

private fun registerFirebase(user: RegisterResponse, callback: (isSuccess: Boolean) -> Unit) {
    logd(TAG, "registerFirebase() - Starting Firebase registration")
    FirebaseMessaging.getInstance().deleteToken()
    Firebase.auth.currentUser?.delete()?.addOnCompleteListener {
        logd(TAG, "registerFirebase() - delete user finish exception:${it.exception}")
        if (it.isSuccessful) {
            logd(TAG, "registerFirebase() - User deleted successfully, attempting custom login")
            firebaseCustomLogin(user.data.customToken) { isSuccessful, error ->
                if (isSuccessful) {
                    logd(TAG, "registerFirebase() - Custom login successful")
                    MixpanelManager.identifyUserProfile()
                    callback(true)
                } else {
                    loge(TAG, "registerFirebase() - Custom login failed: $error")
                    callback(false)
                }
            }
        } else {
            loge(TAG, "registerFirebase() - Failed to delete current user: ${it.exception?.message}")
            callback(false)
        }
    }
}

private suspend fun registerServer(username: String, prefix: String): RegisterResponse {
    logd(TAG, "Starting server registration for Secure Type/COA account (username: $username)")
    logd(TAG, "Note: No mnemonic is generated for Secure Type accounts (hardware-backed keys only)")
    val deviceInfoRequest = DeviceInfoManager.getDeviceInfoRequest()
    val service = retrofit().create(ApiService::class.java)
    val baseDir = File(Env.getApp().filesDir, "wallet")
    val storage = FileSystemStorage(baseDir)

    try {
        // Generate and store mnemonic globally for seed phrase backup support
        val mnemonic = BIP39.generate(BIP39.SeedPhraseLength.TWELVE)
        logd(TAG, "Generated new 12-word mnemonic for backup support")

        val passwordMap = try {
            val pref = readWalletPassword()
            if (pref.isBlank()) {
                HashMap<String, String>()
            } else {
                Gson().fromJson(pref, object : TypeToken<HashMap<String, String>>() {}.type)
            }
        } catch (e: Exception) {
            HashMap<String, String>()
        }

        // Store mnemonic globally (this will make it accessible via Wallet.store().mnemonic())
        storeWalletPassword(Gson().toJson(passwordMap.apply { put("global", mnemonic) }))
        logd(TAG, "Stored mnemonic globally for backup support")

        // Create a new private key
        val privateKey = PrivateKey.create(storage)
        logd(TAG, "Created new private key for registration")

        // Store the private key with prefix as ID for later retrieval
        val keyId = "prefix_key_$prefix"
        privateKey.store(keyId, prefix) // Use prefix as password for simplicity
        logd(TAG, "Stored private key with ID: $keyId")

        // Get the uncompressed public key using the fixed Flow-Wallet-Kit method
        val publicKeyBytes = privateKey.publicKey(SigningAlgorithm.ECDSA_P256)
        if (publicKeyBytes == null) {
            logd(TAG, "Failed to get public key from private key")
            throw IllegalStateException("Failed to get public key from private key")
        }

        logd(TAG, "Public key size: ${publicKeyBytes.size} bytes")

        // Convert public key to hex string, removing "04" prefix if present
        // Flow expects uncompressed public keys without the format indicator
        val hexPublicKey = if (publicKeyBytes.size == 65 && publicKeyBytes[0] == 0x04.toByte()) {
            // Remove the "04" prefix for uncompressed keys
            publicKeyBytes.copyOfRange(1, publicKeyBytes.size).joinToString("") { "%02x".format(it) }
        } else {
            publicKeyBytes.joinToString("") { "%02x".format(it) }
        }
        logd(TAG, "Formatted public key: $hexPublicKey (${hexPublicKey.length} chars)")

        // Create registration request with correct algorithm parameters
        val request = RegisterRequest(
            username = username,
            accountKey = AccountKey(
                publicKey = hexPublicKey
                // Using default values: ECDSA_P256 and SHA2_256
            ),
            deviceInfo = deviceInfoRequest
        )

        logd(TAG, "Sending registration request: $request")
        try {
            val user = service.register(request)
            logd(TAG, "Registration response: $user")
            logd(TAG, "Registration response details:")
            logd(TAG, "  - status: ${user.status}")
            logd(TAG, "  - message: ${user.message}")
            logd(TAG, "  - data.uid: ${user.data.uid}")
            logd(TAG, "  - data.customToken length: ${user.data.customToken.length}")

            if (user.status > 400) {
                logd(TAG, "Registration failed with status: ${user.status}, message: ${user.message}")
                throw IllegalStateException("Registration failed with status: ${user.status}, message: ${user.message}")
            }

            return user
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            logd(TAG, "HTTP Error: ${e.code()}, Response: $errorBody")
            throw e
        }
    } catch (e: Exception) {
        logd(TAG, "Error during server registration: ${e.message}")
        logd(TAG, "Error stack trace: ${e.stackTraceToString()}")
        throw e
    }
}

fun generatePrefix(text: String): String {
    val timestamp = System.currentTimeMillis().toString()
    val combinedInput = "${text}_$timestamp"
    val bytes = MessageDigest.getInstance("SHA-256")
        .digest(combinedInput.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

private suspend fun setToAnonymous(): Boolean {
    if (!isAnonymousSignIn()) {
        Firebase.auth.signOut()
        return signInAnonymously()
    }
    return true
}

// create user failed, resume account
private suspend fun resumeAccount() {
    if (!setToAnonymous()) {
        toast(msgRes = R.string.resume_login_error, duration = Toast.LENGTH_LONG)
        return
    }
    val deviceInfoRequest = DeviceInfoManager.getDeviceInfoRequest()
    val service = retrofit().create(ApiService::class.java)
    val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()
    if (cryptoProvider == null) {
        toast(msgRes = R.string.resume_login_error, duration = Toast.LENGTH_LONG)
        return
    }
    val resp = service.login(
        LoginRequest(
            signature = cryptoProvider.getUserSignature(getFirebaseJwt()),
            accountKey = AccountKey(
                publicKey = cryptoProvider.getPublicKey(),
                hashAlgo = cryptoProvider.getHashAlgorithm().cadenceIndex,
                signAlgo = cryptoProvider.getSignatureAlgorithm().cadenceIndex
            ),
            deviceInfo = deviceInfoRequest
        )
    )
    if (resp.data?.customToken.isNullOrBlank()) {
        toast(msgRes = R.string.resume_login_error, duration = Toast.LENGTH_LONG)
        return
    }
    firebaseLogin(resp.data?.customToken!!) { isSuccess ->
        if (isSuccess) {
            setRegistered()
            if (AccountManager.get()?.prefix == null && AccountManager.get()?.keyStoreInfo == null) {
                Wallet.store().resume()
            }
        } else {
            toast(msgRes = R.string.resume_login_error, duration = Toast.LENGTH_LONG)
            return@firebaseLogin
        }
    }
}

suspend fun clearUserCache() {
    clearCacheDir()
    clearWebViewCache()
    setMeowDomainClaimed(false)
    FungibleTokenListManager.clear()
    WalletManager.clear()
    DAppEVMConnectionManager.clearPreferences()
    NftCollectionStateManager.clear()
    TransactionStateManager.reload()
    StakingManager.clear()
    CryptoProviderManager.clear()
    cleanBackupMnemonicPreference()
    delay(1000)
}

fun clearWebViewCache() {
    WebStorage.getInstance().deleteAllData()
}

/**
 * Wait for a Flow transaction to complete and extract the created address from events
 * Similar to iOS fetchAccountsByCreationTxId implementation
 */
private suspend fun getCreatedAddressFromTx(txId: String): String? {
    return try {
        logd(TAG, "Waiting for transaction $txId to seal...")

        // Use Flow Cadence API to wait for transaction to seal
        val result = FlowCadenceApi.waitForSeal(txId)

        logd(TAG, "Transaction result received")
        logd(TAG, "Transaction status: ${result.status}")
        logd(TAG, "Transaction execution: ${result.execution}")
        logd(TAG, "Transaction error message: ${result.errorMessage}")
        logd(TAG, "Transaction events count: ${result.events.size}")
        logd(TAG, "isExecuteFinished: ${result.isExecuteFinished()}")

        // Check if transaction has events (executed successfully) even if not sealed yet
        // For account creation, events are available once executed
        val hasEvents = result.events.isNotEmpty()
        val hasNoErrors = result.errorMessage.isBlank()

        if (hasEvents && hasNoErrors) {
            logd(TAG, "Transaction has events and no errors, attempting to extract address...")
            // Transaction succeeded, extract created address from events
            // Look for flow.AccountCreated event
            val createdEvent = result.events.find { it.type.contains("flow.AccountCreated") }

            if (createdEvent != null) {
                // Try to extract address from event payload
                // The event structure typically has: { address: "0x..." }
                val eventPayload = createdEvent.payload
                logd(TAG, "AccountCreated event payload: $eventPayload")
                logd(TAG, "Event payload type: ${eventPayload.javaClass.name}")

                // Try to parse the address field from the payload
                try {
                    // The payload is a Cadence.Value.EventValue containing a CompositeValue
                    logd(TAG, "Attempting to extract address from event payload...")

                    // Access the actual value inside the EventValue
                    if (eventPayload is org.onflow.flow.infrastructure.Cadence.Value.EventValue) {
                        val compositeValue = eventPayload.value
                        logd(TAG, "Composite value: $compositeValue")
                        logd(TAG, "Composite value type: ${compositeValue.javaClass.name}")

                        if (compositeValue is org.onflow.flow.infrastructure.Cadence.CompositeValue) {
                            // Get the fields from the composite value
                            val fields = compositeValue.fields
                            logd(TAG, "Composite fields count: ${fields.size}")

                            // Find the address field
                            val addressField = fields.find { it.name == "address" }
                            if (addressField != null) {
                                logd(TAG, "Found address field: ${addressField.value}")
                                logd(TAG, "Address field type: ${addressField.value.javaClass.name}")

                                // The address value should be an AddressValue
                                if (addressField.value is org.onflow.flow.infrastructure.Cadence.Value.AddressValue) {
                                    val addressValue = addressField.value as org.onflow.flow.infrastructure.Cadence.Value.AddressValue
                                    val rawAddress = addressValue.value
                                    logd(TAG, "Extracted raw Flow address: $rawAddress")

                                    // Format the address: remove leading zeros and ensure 0x prefix
                                    val cleanAddress = rawAddress.trimStart('0')
                                    val formattedAddress = if (cleanAddress.startsWith("0x")) {
                                        cleanAddress
                                    } else {
                                        "0x$cleanAddress"
                                    }
                                    logd(TAG, "Formatted Flow address: $formattedAddress")
                                    return formattedAddress
                                } else {
                                    // Try to extract from string representation
                                    val addressString = addressField.value.toString()
                                    logd(TAG, "Address value string: $addressString")

                                    // Look for hex address pattern (with or without 0x prefix)
                                    // Flow addresses can be 40 chars (full format) or 16 chars (short format)
                                    val addressRegex = Regex("[0-9a-fA-F]{16,40}")
                                    val addressMatch = addressRegex.find(addressString)

                                    if (addressMatch != null) {
                                        val rawAddress = addressMatch.value
                                        logd(TAG, "Found raw address via regex: $rawAddress")

                                        // Trim leading zeros and add 0x prefix
                                        val cleanAddress = rawAddress.trimStart('0')
                                        val formattedAddress = "0x$cleanAddress"
                                        logd(TAG, "Formatted address: $formattedAddress")
                                        return formattedAddress
                                    }
                                }
                            } else {
                                loge(TAG, "No 'address' field found in composite value")
                                logd(TAG, "Available fields: ${fields.map { it.name }.joinToString()}")
                            }
                        }
                    }

                    loge(TAG, "Could not extract address from event payload structure")
                } catch (e: Exception) {
                    loge(TAG, "Could not extract address from payload: ${e.message}")
                    loge(TAG, "Stack trace: ${e.stackTraceToString()}")
                }

                loge(TAG, "Could not extract address from AccountCreated event")
            } else {
                loge(TAG, "Transaction completed but no AccountCreated event found")
                logd(TAG, "All events in transaction:")
                result.events.forEach { event ->
                    logd(TAG, "  - Event type: ${event.type}")
                }
            }
        }
        null
    } catch (e: Exception) {
        loge(TAG, "Failed to get created address from transaction: ${e.message}")
        e.printStackTrace()
        null
    }
}
