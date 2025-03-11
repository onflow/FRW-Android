package com.flowfoundation.wallet.page.walletrestore

import androidx.annotation.WorkerThread
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.flowfoundation.wallet.firebase.auth.deleteAnonymousUser
import com.flowfoundation.wallet.firebase.auth.firebaseCustomLogin
import com.flowfoundation.wallet.firebase.auth.getFirebaseJwt
import com.flowfoundation.wallet.firebase.auth.isAnonymousSignIn
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.manager.key.HDWalletCryptoProvider
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.clearUserCache
import com.flowfoundation.wallet.network.model.AccountKey
import com.flowfoundation.wallet.network.model.LoginRequest
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.setRegistered
import com.flowfoundation.wallet.wallet.Wallet
import kotlinx.coroutines.runBlocking
import wallet.core.jni.HDWallet


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

fun requestWalletRestoreLogin(
    mnemonic: String,
    callback: (isSuccess: Boolean, reason: Int?) -> Unit
) {
    ioScope {
        val cryptoProvider = HDWalletCryptoProvider(HDWallet(mnemonic, ""))
        getFirebaseUid { uid ->
            if (uid.isNullOrBlank()) {
                callback.invoke(false, ERROR_UID)
                return@getFirebaseUid
            }
            runBlocking {
                val catching = runCatching {
                    val deviceInfoRequest = DeviceInfoManager.getDeviceInfoRequest()
                    val service = retrofit().create(ApiService::class.java)
                    val resp = service.login(
                        LoginRequest(
                            signature = cryptoProvider.getUserSignature(
                                getFirebaseJwt()
                            ),
                            accountKey = AccountKey(
                                publicKey = cryptoProvider.getPublicKey(),
                                hashAlgo = cryptoProvider.getHashAlgorithm().index,
                                signAlgo = cryptoProvider.getSignatureAlgorithm().index
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
                                    AccountManager.add(Account(userInfo = service.userInfo().data))
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
                    loge(catching.exceptionOrNull())
                    callback.invoke(false, ERROR_NETWORK)
                }
            }
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