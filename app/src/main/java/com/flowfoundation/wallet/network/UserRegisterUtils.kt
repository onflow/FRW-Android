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

private const val TAG = "UserRegisterUtils"

// register one step, create user & create wallet
suspend fun registerOutblock(
    username: String,
) = suspendCoroutine { continuation ->
    ioScope {
        registerOutblockUserInternal(username) { isSuccess, prefix ->
            ioScope {
                if (isSuccess) {
                    createWalletFromServer()
                    setRegistered()

                    val baseDir = File(Env.getApp().filesDir, "wallet")
                    val storage = FileSystemStorage(baseDir)
                    
                    // Create a new private key using Trust Wallet Core
                    val privateKey = PrivateKey.create(storage)
                    logd(TAG, "Created new private key")
                    
                    // Store the private key with prefix as ID for later retrieval
                    val keyId = "prefix_key_$prefix"
                    privateKey.store(keyId, prefix) // Use prefix as password for simplicity
                    logd(TAG, "Stored private key with ID: $keyId")
                    
                    val publicKeyBytes = privateKey.publicKey(SigningAlgorithm.ECDSA_P256)
                    if (publicKeyBytes == null) {
                        logd(TAG, "Failed to get public key from private key")
                        continuation.resume(false)
                        return@ioScope
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
                    
                    // Get device info for registration
                    val deviceInfoRequest = DeviceInfoManager.getDeviceInfoRequest()
                    logd(TAG, "Got device info request")
                    
                    val service = retrofit().create(ApiService::class.java)
                    val user = service.register(
                        RegisterRequest(
                            username = username,
                            accountKey = AccountKey(
                                publicKey = hexPublicKey
                                // Using default values: ECDSA_P256 and SHA2_256
                            ),
                            deviceInfo = deviceInfoRequest
                        )
                    )
                    logd(TAG, "Registered user: $user")
                    
                    // Create a key wallet using the private key directly
                    val wallet = WalletFactory.createKeyWallet(
                        privateKey,
                        setOf(ChainId.Mainnet, ChainId.Testnet),
                        storage
                    )
                    logd(TAG, "Created key wallet")
                    
                    WalletManager.init()

                    val account = Account(
                        userInfo = service.userInfo().data,
                        prefix = prefix,
                        wallet = service.getWalletList().data
                    )
                    logd(TAG, "Adding account to AccountManager: $account")
                    
                    // Manually add accounts from server to the wallet
                    // This is needed because new accounts may not be indexed yet by the key indexer
                    account.wallet?.wallets?.forEach { walletData ->
                        walletData.blockchain?.forEach { blockchain ->
                            try {
                                val chainId = when (blockchain.chainId?.lowercase()) {
                                    "mainnet" -> ChainId.Mainnet
                                    "testnet" -> ChainId.Testnet
                                    else -> null
                                }
                                
                                if (chainId != null && !blockchain.address.isNullOrBlank()) {
                                    logd(TAG, "Fetching account ${blockchain.address} from ${blockchain.chainId} using Flow API")
                                    
                                    // Use the wallet's fetchAccountByAddress method to get the account directly
                                    // from the Flow network, bypassing the key indexer
                                    runBlocking {
                                        wallet.fetchAccountByAddress(blockchain.address!!, chainId)
                                    }
                                    logd(TAG, "Successfully fetched and added account ${blockchain.address} to wallet")
                                }
                            } catch (e: Exception) {
                                logd(TAG, "Error fetching account ${blockchain.address}: ${e.message}")
                            }
                        }
                    }
                    
                    AccountManager.add(
                        account,
                        firebaseUid()
                    )
                    logd(TAG, "Account added, getting crypto provider")
                    
                    // Ensure account is properly set before generating crypto provider
                    val currentAccount = AccountManager.get()
                    if (currentAccount == null) {
                        logd(TAG, "Failed to get current account after adding")
                        continuation.resume(false)
                        return@ioScope
                    }
                    
                    val cryptoProvider = CryptoProviderManager.generateAccountCryptoProvider(currentAccount)
                    logd(TAG, "Crypto provider generated: ${cryptoProvider != null}")
                    
                    if (cryptoProvider == null) {
                        logd(TAG, "Failed to generate crypto provider")
                        continuation.resume(false)
                        return@ioScope
                    }
                    
                    MixpanelManager.accountCreated(
                        cryptoProvider.getPublicKey(),
                        AccountCreateKeyType.KEY_STORE,
                        cryptoProvider.getSignatureAlgorithm().value,
                        cryptoProvider.getHashAlgorithm().algorithm
                    )
                    clearUserCache()
                    continuation.resume(true)
                } else {
                    resumeAccount()
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
