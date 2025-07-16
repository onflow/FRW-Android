package com.flowfoundation.wallet.page.walletrestore

import androidx.annotation.WorkerThread
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.flowfoundation.wallet.firebase.auth.deleteAnonymousUser
import com.flowfoundation.wallet.firebase.auth.firebaseCustomLogin
import com.flowfoundation.wallet.firebase.auth.firebaseUid
import com.flowfoundation.wallet.firebase.auth.getFirebaseJwt
import com.flowfoundation.wallet.firebase.auth.isAnonymousSignIn
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.manager.key.HDWalletCryptoProvider
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.clearUserCache
import com.flowfoundation.wallet.network.generatePrefix
import com.flowfoundation.wallet.network.model.AccountKey
import com.flowfoundation.wallet.network.model.LoginRequest
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.setRegistered
import com.flowfoundation.wallet.wallet.Wallet
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.storage.FileSystemStorage
import com.flowfoundation.wallet.utils.Env
import org.onflow.flow.models.hexToBytes
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File

const val WALLET_RESTORE_STEP_GUIDE = 0
const val WALLET_RESTORE_STEP_DRIVE_USERNAME = 1
const val WALLET_RESTORE_STEP_DRIVE_PASSWORD = 2
const val WALLET_RESTORE_STEP_MNEMONIC = 3
const val WALLET_RESTORE_ERROR = 4
const val WALLET_RESTORE_BACKUP_NOT_FOUND = 5

const val ERROR_CUSTOM_TOKEN = 1
const val ERROR_UID = 2
const val ERROR_FIREBASE_SIGN_IN = 3
const val ERROR_NETWORK = 4
const val ERROR_ACCOUNT_NOT_FOUND = 5

private const val TAG = "WalletRestoreUtils"

/**
 * Create a SeedPhraseKey with properly initialized keyPair
 * This fixes the "Signing key is empty or not available" error
 */
@OptIn(ExperimentalStdlibApi::class)
private fun createSeedPhraseKeyWithKeyPair(mnemonic: String, storage: FileSystemStorage): SeedPhraseKey {
    logd(TAG, "Creating SeedPhraseKey with proper keyPair initialization")

    try {
        // Create a simple dummy KeyPair to pass the null check in sign()
        // The actual signing uses hdWallet.getKeyByCurve() internally, not this keyPair
        val keyGenerator = java.security.KeyPairGenerator.getInstance("EC")
        keyGenerator.initialize(256)
        val dummyKeyPair = keyGenerator.generateKeyPair()

        logd(TAG, "Created dummy KeyPair for null check")

        // Create SeedPhraseKey with the dummy keyPair
        val seedPhraseKey = SeedPhraseKey(
            mnemonicString = mnemonic,
            passphrase = "",
            derivationPath = "m/44'/539'/0'/0/0",
            keyPair = dummyKeyPair,
            storage = storage
        )

        // Verify that the SeedPhraseKey can generate keys using its internal hdWallet
        try {
            val publicKey = seedPhraseKey.publicKey(org.onflow.flow.models.SigningAlgorithm.ECDSA_secp256k1)
                ?: throw RuntimeException("SeedPhraseKey failed to generate public key")
            logd(TAG, "SeedPhraseKey successfully verified with public key: ${publicKey.toHexString().take(20)}...")
        } catch (e: Exception) {
            throw RuntimeException("SeedPhraseKey verification failed: ${e.message}", e)
        }

        return seedPhraseKey
    } catch (e: Exception) {
        loge(TAG, "Failed to create SeedPhraseKey with keyPair: ${e.message}")
        throw RuntimeException("SeedPhraseKey creation failed: ${e.message}", e)
    }
}

@OptIn(ExperimentalStdlibApi::class)
fun requestWalletRestoreLogin(
    mnemonic: String,
    callback: (isSuccess: Boolean, reason: Int?) -> Unit
) {
    ioScope {
        try {
            // Validate mnemonic format first
            if (mnemonic.isBlank()) {
                loge(TAG, "Empty mnemonic provided to requestWalletRestoreLogin")
                callback.invoke(false, ERROR_NETWORK)
                return@ioScope
            }
            
            val words = mnemonic.trim().split("\\s+".toRegex())
            if (words.size != 12 && words.size != 15 && words.size != 24) {
                loge(TAG, "Invalid mnemonic word count: ${words.size}")
                callback.invoke(false, ERROR_NETWORK)
                return@ioScope
            }
            
            logd(TAG, "Creating crypto provider for Google Drive restore with ${words.size} word mnemonic")
            
            val baseDir = File(Env.getApp().filesDir, "wallet")
            
            // Use the same working pattern as multi-restore: create SeedPhraseKey with dummy keyPair
            // to pass the null check in the KMM layer's sign() method
            val seedPhraseKey = createSeedPhraseKeyWithKeyPair(mnemonic, FileSystemStorage(baseDir))
            
            // Create HDWalletCryptoProvider (same as seed phrase restore, weight 1000)
            val cryptoProvider = try {
                HDWalletCryptoProvider(seedPhraseKey)
            } catch (e: Exception) {
                loge(TAG, "Failed to create HDWalletCryptoProvider: ${e.message}")
                callback.invoke(false, ERROR_NETWORK)
                return@ioScope
            }
            
            // Validate the crypto provider before attempting to use it
            val publicKey = try {
                cryptoProvider.getPublicKey()
            } catch (e: Exception) {
                loge(TAG, "Failed to get public key from HDWalletCryptoProvider: ${e.message}")
                callback.invoke(false, ERROR_NETWORK)
                return@ioScope
            }
            
            if (publicKey.isBlank() || publicKey == "0x" || publicKey.length < 64) {
                loge(TAG, "Invalid public key from crypto provider: $publicKey")
                callback.invoke(false, ERROR_NETWORK)
                return@ioScope
            }
            
            logd(TAG, "HDWalletCryptoProvider created successfully with public key: ${publicKey.take(20)}...")
            
            getFirebaseUid { uid ->
                if (uid.isNullOrBlank()) {
                    callback.invoke(false, ERROR_UID)
                    return@getFirebaseUid
                }
                runBlocking {
                    val catching = runCatching {
                        val deviceInfoRequest = DeviceInfoManager.getDeviceInfoRequest()
                        val service = retrofit().create(ApiService::class.java)
                        
                        // Test signature creation before making the request
                        val testSignature = try {
                            val jwt = getFirebaseJwt()
                            cryptoProvider.getUserSignature(jwt)
                        } catch (e: Exception) {
                            loge(TAG, "Failed to create test signature: ${e.message}")
                            throw RuntimeException("Crypto provider signature creation failed: ${e.message}")
                        }
                        
                        if (testSignature.isBlank()) {
                            throw RuntimeException("Crypto provider returned empty signature")
                        }
                        
                        logd(TAG, "Test signature created successfully")
                        
                        val resp = service.login(
                            LoginRequest(
                                signature = testSignature,
                                accountKey = AccountKey(
                                    publicKey = cryptoProvider.getPublicKey(),
                                    hashAlgo = cryptoProvider.getHashAlgorithm().cadenceIndex,
                                    signAlgo = cryptoProvider.getSignatureAlgorithm().cadenceIndex
                                ),
                                deviceInfo = deviceInfoRequest
                            )
                        )
                        if (resp.data?.customToken.isNullOrBlank()) {
                            if (resp.status == 404) {
                                callback.invoke(false, ERROR_ACCOUNT_NOT_FOUND)
                            } else {
                                callback.invoke(false, ERROR_CUSTOM_TOKEN)
                            }
                        } else {
                            firebaseLogin(resp.data?.customToken!!) { isSuccess ->
                                if (isSuccess) {
                                    setRegistered()
                                    Wallet.store().reset(mnemonic)
                                    ioScope {
                                        val userInfo = service.userInfo().data
                                        
                                        // Generate prefix for this account (like multi-restore)
                                        val prefix = generatePrefix(userInfo.username)
                                        logd(TAG, "Generated prefix for Google Drive restore: $prefix")
                                        
                                        // Store the mnemonic-derived private key using the prefix pattern
                                        // This allows CryptoProviderManager to find it later
                                        try {
                                            val baseDir = File(Env.getApp().filesDir, "wallet")
                                            val storage = FileSystemStorage(baseDir)
                                            val keyId = "prefix_key_$prefix"
                                            
                                            // Extract private key from our seedPhraseKey and store it with prefi
                                            val privateKeyHex = seedPhraseKey.privateKey(cryptoProvider.getSignatureAlgorithm())?.toHexString()
                                            if (privateKeyHex.isNullOrBlank()) {
                                                throw RuntimeException("Failed to extract private key for storage")
                                            }
                                            
                                            // Create and store a PrivateKey instance for prefix-based access
                                            val privateKey = com.flow.wallet.keys.PrivateKey.create(storage)
                                            privateKey.importPrivateKey(privateKeyHex.removePrefix("0x").hexToBytes(), com.flow.wallet.keys.KeyFormat.RAW)
                                            privateKey.store(keyId, prefix)
                                            
                                            logd(TAG, "Stored private key with prefix: $keyId")
                                            
                                            // Create account with prefix (like multi-restore)
                                            AccountManager.add(
                                                Account(
                                                    userInfo = userInfo,
                                                    prefix = prefix
                                                ),
                                                firebaseUid()
                                            )
                                            
                                            logd(TAG, "Successfully created account with prefix: $prefix")
                                        } catch (e: Exception) {
                                            loge(TAG, "Failed to store key with prefix: ${e.message}")
                                            // Fallback to basic account creation
                                            AccountManager.add(Account(userInfo = userInfo))
                                        }
                                        
                                        clearUserCache()
                                        callback.invoke(true, null)
                                    }
                                } else {
                                    callback.invoke(false, ERROR_FIREBASE_SIGN_IN)
                                }
                            }
                        }
                    }

                    if (catching.isFailure) {
                        loge(TAG, "Login request failed: ${catching.exceptionOrNull()?.message}")
                        loge(catching.exceptionOrNull())
                        callback.invoke(false, ERROR_NETWORK)
                    }
                }
            }
        } catch (e: Exception) {
            loge(TAG, "requestWalletRestoreLogin failed: ${e.message}")
            loge(e)
            callback.invoke(false, ERROR_NETWORK)
        }
    }
}

@WorkerThread
suspend fun firebaseLogin(customToken: String, callback: (isSuccess: Boolean) -> Unit) {
    logd(TAG, "start delete user")
    val isSuccess = if (isAnonymousSignIn()) {
        deleteAnonymousUser()
    } else {
        // wallet reset
        Firebase.auth.signOut()
        true
    }
    if (isSuccess) {
        // Add a delay to ensure Firebase auth state is cleared
        delay(1000)
        firebaseCustomLogin(customToken) { isSuccessful, _ ->
            if (isSuccessful) {
                MixpanelManager.identifyUserProfile()
                callback(true)
            } else callback(false)
        }
    } else callback(false)
}

suspend fun getFirebaseUid(callback: (uid: String?) -> Unit) {
    val uid = Firebase.auth.currentUser?.uid
    if (!uid.isNullOrBlank()) {
        callback.invoke(uid)
        return
    }

    getFirebaseJwt(true)

    callback.invoke(Firebase.auth.currentUser?.uid)
}