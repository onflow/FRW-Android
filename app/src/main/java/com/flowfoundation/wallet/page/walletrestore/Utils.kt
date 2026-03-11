package com.flowfoundation.wallet.page.walletrestore

import androidx.annotation.WorkerThread
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.flowfoundation.wallet.firebase.auth.firebaseCustomLogin
import com.flowfoundation.wallet.firebase.auth.getFirebaseJwt
import com.flowfoundation.wallet.firebase.auth.setToAnonymous
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.manager.key.HDWalletCryptoProvider
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.clearUserCache
import com.flowfoundation.wallet.network.model.AccountKey
import com.flowfoundation.wallet.network.model.WalletListData
import com.flowfoundation.wallet.network.model.FlowAccountInfo
import com.flowfoundation.wallet.network.model.LoginV4Request
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.setRegistered
import com.flowfoundation.wallet.wallet.Wallet
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.storage.FileSystemStorage
import com.flowfoundation.wallet.firebase.auth.firebaseUid
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.wallet.DERIVATION_PATH
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
        // Create SeedPhraseKey
        val seedPhraseKey = SeedPhraseKey(
            mnemonicString = mnemonic,
            passphrase = "",
            derivationPath = DERIVATION_PATH,
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

            // Ensure we are in anonymous state before fetching the UID, mirroring iOS behaviour.
            // signInWithCustomToken will atomically replace the anonymous user later.
            if (!setToAnonymous()) {
                loge(TAG, "setToAnonymous failed before wallet restore login")
                callback.invoke(false, ERROR_UID)
                return@ioScope
            }

            getFirebaseUid { uid ->
                if (uid.isNullOrBlank()) {
                    callback.invoke(false, ERROR_UID)
                    return@getFirebaseUid
                }
                runBlocking {
                    val catching = runCatching {
                        val deviceInfoRequest = DeviceInfoManager.getDeviceInfoRequest()
                        val service = retrofit().create(ApiService::class.java)

                        // Fetch JWT once; reuse for both Flow and EVM signatures.
                        val jwt = getFirebaseJwt()

                        val flowSignature = try {
                            cryptoProvider.getUserSignature(jwt)
                        } catch (e: Exception) {
                            loge(TAG, "Failed to create test signature: ${e.message}")
                            throw RuntimeException("Crypto provider signature creation failed: ${e.message}")
                        }

                        if (flowSignature.isBlank()) {
                            throw RuntimeException("Crypto provider returned empty signature")
                        }

                        logd(TAG, "Test signature created successfully")

                        val accountKey = AccountKey(
                            publicKey = cryptoProvider.getPublicKey(),
                            hashAlgo = cryptoProvider.getHashAlgorithm().cadenceIndex,
                            signAlgo = cryptoProvider.getSignatureAlgorithm().cadenceIndex
                        )
                        val evmAccountInfo = cryptoProvider.getEvmAccountInfo(jwt)
                        val loginRequest = LoginV4Request(
                            flowAccountInfo = FlowAccountInfo(accountKey = accountKey, signature = flowSignature),
                            evmAccountInfo = evmAccountInfo,
                            deviceInfo = deviceInfoRequest
                        )
                        val resp = service.loginV4(loginRequest)
                        if (resp.data?.customToken.isNullOrBlank()) {
                            if (resp.status == 404) {
                                callback.invoke(false, ERROR_ACCOUNT_NOT_FOUND)
                            } else {
                                callback.invoke(false, ERROR_CUSTOM_TOKEN)
                            }
                        } else {
                            firebaseLogin(resp.data.customToken) { isSuccess ->
                                if (isSuccess) {
                                    setRegistered()
                                    Wallet.store().reset(mnemonic)
                                    ioScope {
                                        val userInfo = service.userInfo().data
                                        val userId = firebaseUid() ?: ""
                                        val walletData = WalletListData(
                                            id = userId,
                                            username = userInfo.username,
                                            wallets = null
                                        )
                                        clearUserCache()
                                        AccountManager.add(
                                            Account(
                                                userInfo = userInfo,
                                                wallet = walletData
                                            )
                                        )
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
    logd(TAG, "=== firebaseLogin START ===")
    logd(TAG, "Custom token received, length: ${customToken.length}")
    logd(TAG, "Current user UID: ${Firebase.auth.currentUser?.uid}")

    // signInWithCustomToken atomically replaces the current user (anonymous or otherwise)
    // without going through a null state. Prior delete/signOut calls created a null window
    // that raced with background getFirebaseJwt() → signInAnonymously() calls.
    firebaseCustomLogin(customToken) { isSuccessful, errorMsg ->
        logd(TAG, "Firebase custom login completed - success: $isSuccessful, error: $errorMsg")
        if (isSuccessful) {
            logd(TAG, "Firebase login successful, new UID: ${Firebase.auth.currentUser?.uid}")
            MixpanelManager.identifyUserProfile()
            callback(true)
        } else {
            logd(TAG, "ERROR: Firebase custom login failed - $errorMsg")
            callback(false)
        }
    }
}

suspend fun getFirebaseUid(callback: (uid: String?) -> Unit) {
    logd(TAG, "=== getFirebaseUid START ===")
    val currentUser = Firebase.auth.currentUser
    logd(TAG, "Current Firebase user: ${currentUser?.uid ?: "null"}")
    logd(TAG, "Is anonymous: ${currentUser?.isAnonymous ?: "n/a"}")

    val uid = currentUser?.uid
    if (!uid.isNullOrBlank()) {
        logd(TAG, "Firebase UID already available: $uid")
        callback.invoke(uid)
        return
    }

    logd(TAG, "No Firebase UID found, attempting to get Firebase JWT")
    getFirebaseJwt(true)

    val newUid = Firebase.auth.currentUser?.uid
    logd(TAG, "After getFirebaseJwt - new UID: ${newUid ?: "still null"}")
    callback.invoke(newUid)
}
