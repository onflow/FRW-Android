package com.flowfoundation.wallet.network

import android.webkit.WebStorage
import android.widget.Toast
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.firebase.auth.firebaseCustomLogin
import com.flowfoundation.wallet.firebase.auth.firebaseUid
import com.flowfoundation.wallet.firebase.auth.getFirebaseJwt
import com.flowfoundation.wallet.firebase.auth.isAnonymousSignIn
import com.flowfoundation.wallet.firebase.auth.signInAnonymously
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.BalanceManager
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.coin.TokenStateManager
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.nft.NftCollectionStateManager
import com.flowfoundation.wallet.manager.staking.StakingManager
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.mixpanel.AccountCreateKeyType
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.network.model.AccountKey
import com.flowfoundation.wallet.network.model.LoginRequest
import com.flowfoundation.wallet.network.model.RegisterRequest
import com.flowfoundation.wallet.network.model.RegisterResponse
import com.flowfoundation.wallet.page.walletrestore.firebaseLogin
import com.flowfoundation.wallet.utils.cleanBackupMnemonicPreference
import com.flowfoundation.wallet.utils.clearCacheDir
import com.flowfoundation.wallet.utils.error.AccountError
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.error.WalletError
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.setMeowDomainClaimed
import com.flowfoundation.wallet.utils.setRegistered
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.wallet.Wallet
import com.flowfoundation.wallet.wallet.createWalletFromServer
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.storage.FileSystemStorage
import kotlinx.coroutines.delay
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import com.flowfoundation.wallet.utils.Env
import java.io.File
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.keys.KeyFormat
import com.flow.wallet.wallet.WalletFactory
import org.onflow.flow.ChainId
import org.onflow.flow.models.SigningAlgorithm
import com.google.common.io.BaseEncoding
import org.onflow.flow.models.HashingAlgorithm
import kotlinx.coroutines.runBlocking
import com.flow.wallet.crypto.BIP39
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.utils.readWalletPassword
import com.flowfoundation.wallet.utils.storeWalletPassword
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

private const val TAG = "UserRegisterUtils"

// register one step, create user & create wallet
@OptIn(ExperimentalStdlibApi::class)
suspend fun registerOutblock(
    username: String,
) = suspendCoroutine { continuation ->
    ioScope {
        // registerOutblockUserInternal will call registerServer, which creates and stores
        // the primary private key associated with the prefix, and performs the actual
        // server registration using that key's public key.
        registerOutblockUserInternal(username) { isSuccess, prefix ->
            ioScope {
                if (isSuccess) {
                    // At this point, user is registered on server, Firebase is synced,
                    // and the correct private key (from registerServer) is stored with the prefix.
                    
                    // Declare service here for fetching user and wallet info
                    val service = retrofit().create(ApiService::class.java)

                    createWalletFromServer() // This should ideally ensure the WalletManager is aware of the new account
                    setRegistered()

                    // Wallet and Account object creation should use data from the successful registration (via registerServer)
                    // The service calls here should ideally just fetch the latest state if needed,
                    // not perform new registrations or key creations.

                    val userInfo = try { service.userInfo().data } catch (e: Exception) { 
                        logd(TAG, "Failed to fetch user info after registration")
                        continuation.resume(false)
                        return@ioScope
                    }
                    
                    // Use key indexer instead of deprecated getWalletList()
                    val walletListData = try {
                        // Get the public key from the stored private key
                        val storage = FileSystemStorage(File(Env.getApp().filesDir, "wallet"))
                        val keyForWalletSDK = PrivateKey.get("prefix_key_$prefix", prefix, storage)
                        val publicKeyBytes = keyForWalletSDK.publicKey(SigningAlgorithm.ECDSA_P256)
                        val publicKeyHex = publicKeyBytes?.toHexString()?.removePrefix("0x") ?: ""
                        
                        val chainId = when (chainNetWorkString()) {
                            "mainnet" -> ChainId.Mainnet
                            "testnet" -> ChainId.Testnet
                            else -> ChainId.Mainnet
                        }
                        
                        logd(TAG, "Using key indexer to find wallet for public key: $publicKeyHex")
                        val keyIndexerResponse = com.flow.wallet.Network.findAccount(publicKeyHex, chainId)
                        
                        if (keyIndexerResponse.accounts.isNotEmpty()) {
                            // Convert key indexer response to WalletListData format
                            val account = keyIndexerResponse.accounts.first()
                            val blockchainData = com.flowfoundation.wallet.network.model.BlockchainData(
                                address = account.address,
                                chainId = chainNetWorkString()
                            )
                            val walletData = com.flowfoundation.wallet.network.model.WalletData(
                                blockchain = listOf(blockchainData),
                                name = "Flow Wallet"
                            )
                            com.flowfoundation.wallet.network.model.WalletListData(
                                id = userInfo?.username ?: "user",
                                username = userInfo?.username ?: "user",
                                wallets = listOf(walletData)
                            )
                        } else {
                            logd(TAG, "No accounts found in key indexer for registered user")
                            null
                        }
                    } catch (e: Exception) {
                        logd(TAG, "Failed to fetch wallet using key indexer after registration: ${e.message}")
                        continuation.resume(false)
                        return@ioScope
                    }
                    
                    if (walletListData == null) {
                        logd(TAG, "No wallet data found for registered user")
                        continuation.resume(false)
                        return@ioScope
                    }

                    // The WalletFactory.createKeyWallet here might be redundant if WalletManager.init()
                    // correctly initializes based on the prefix and the key stored by registerServer.
                    // However, ensuring the Wallet SDK instance is aware of the account is important.
                    val storage = FileSystemStorage(File(Env.getApp().filesDir, "wallet"))
                    val keyForWalletSDK = try {
                        PrivateKey.get("prefix_key_$prefix", prefix, storage)
                    } catch (e: Exception) {
                        logd(TAG, "Failed to retrieve stored private key for Wallet SDK init.")
                        continuation.resume(false)
                        return@ioScope
                    }
                    val walletForSDK = WalletFactory.createKeyWallet(
                        keyForWalletSDK,
                        setOf(ChainId.Mainnet, ChainId.Testnet),
                        storage
                    )
                    logd(TAG, "Created KeyWallet instance for Wallet SDK.")

                    // Initialize WalletManager to pick up the new account/wallet state
                    // WalletManager.init() should now find the key stored by registerServer via the prefix.
                    WalletManager.init()

                    val account = Account(
                        userInfo = userInfo,
                        prefix = prefix, // This prefix matches the one used to store the key in registerServer
                        wallet = walletListData
                    )
                    logd(TAG, "Adding account to AccountManager: $account")
                    
                    // Manually add accounts from server to the walletForSDK instance
                    account.wallet?.wallets?.forEach { walletData ->
                        walletData.blockchain?.forEach { blockchain ->
                            try {
                                val chainId = when (blockchain.chainId?.lowercase()) {
                                    "mainnet" -> ChainId.Mainnet
                                    "testnet" -> ChainId.Testnet
                                    else -> null
                                }
                                if (chainId != null && !blockchain.address.isNullOrBlank()) {
                                    val address = if (blockchain.address.startsWith("0x")) blockchain.address else "0x${blockchain.address}"
                                    runBlocking {
                                        logd(TAG, "Fetching account $address for $chainId into Wallet SDK instance")
                                        walletForSDK.fetchAccountByAddress(address, chainId)
                                    }
                                }
                            } catch (e: Exception) {
                                loge(TAG, "Error fetching account ${blockchain.address} into Wallet SDK: ${e.message}")
                            }
                        }
                    }
                    
                    AccountManager.add(
                        account,
                        firebaseUid()
                    )
                    logd(TAG, "Account added to AccountManager.")
                    
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
                    continuation.resume(true)
                } else {
                    // Registration failed in registerOutblockUserInternal (e.g., server or Firebase issue)
                    loge(TAG, "registerOutblockUserInternal indicated failure.")
                    // resumeAccount() // This was here, consider if it's needed or if failure is handled by caller
                    continuation.resume(false)
                }
            }
        }
    }
}

private suspend fun registerOutblockUserInternal(
    username: String,
    callback: (isSuccess: Boolean, prefix: String) -> Unit,
) {
    val prefix = generatePrefix(username)
    try {
        if (!setToAnonymous()) {
            resumeAccount()
            callback.invoke(false, prefix)
            return
        }
        val user = registerServer(username, prefix)

        if (user.status > 400) {
            callback(false, prefix)
            return
        }
        logd(TAG, "SYNC Register userId:::${user.data.uid}")
        logd(TAG, "start delete user")
        registerFirebase(user) { isSuccess ->
            callback.invoke(isSuccess, prefix)
        }
    } catch (e: Exception) {
        if (e is IllegalStateException) {
            ErrorReporter.reportCriticalWithMixpanel(WalletError.KEY_STORE_FAILED, e)
        } else {
            ErrorReporter.reportWithMixpanel(AccountError.REGISTER_USER_FAILED, e)
        }
        callback.invoke(false, prefix)
    }
}

private fun registerFirebase(user: RegisterResponse, callback: (isSuccess: Boolean) -> Unit) {
    FirebaseMessaging.getInstance().deleteToken()
    Firebase.auth.currentUser?.delete()?.addOnCompleteListener {
        logd(TAG, "delete user finish exception:${it.exception}")
        if (it.isSuccessful) {
            firebaseCustomLogin(user.data.customToken) { isSuccessful, _ ->
                if (isSuccessful) {
                    MixpanelManager.identifyUserProfile()
                    callback(true)
                } else callback(false)
            }
        } else callback(false)
    }
}

private suspend fun registerServer(username: String, prefix: String): RegisterResponse {
    logd(TAG, "Starting server registration for username: $username")
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
    TokenStateManager.clear()
    WalletManager.clear()
    NftCollectionStateManager.clear()
    TransactionStateManager.reload()
    FlowCoinListManager.reload()
    BalanceManager.clear()
    StakingManager.clear()
    CryptoProviderManager.clear()
    cleanBackupMnemonicPreference()
    delay(1000)
}

fun clearWebViewCache() {
    WebStorage.getInstance().deleteAllData()
}
