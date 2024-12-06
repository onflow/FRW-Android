package com.flowfoundation.wallet.network

import android.webkit.WebStorage
import android.widget.Toast
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.nftco.flow.sdk.HashAlgorithm
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
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.setMeowDomainClaimed
import com.flowfoundation.wallet.utils.setRegistered
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.updateAccountTransferCount
import com.flowfoundation.wallet.wallet.Wallet
import com.flowfoundation.wallet.wallet.createWalletFromServer
import io.outblock.wallet.KeyManager
import io.outblock.wallet.toFormatString
import kotlinx.coroutines.delay
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


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

                    val service = retrofit().create(ApiService::class.java)
                    val account = Account(
                        userInfo = service.userInfo().data,
                        prefix = prefix
                    )
                    AccountManager.add(
                        account,
                        firebaseUid()
                    )
                    val cryptoProvider = CryptoProviderManager.generateAccountCryptoProvider(account)
                    MixpanelManager.accountCreated(
                        cryptoProvider?.getPublicKey().orEmpty(),
                        AccountCreateKeyType.KEY_STORE,
                        cryptoProvider?.getSignatureAlgorithm()?.id.orEmpty(),
                        cryptoProvider?.getHashAlgorithm()?.algorithm.orEmpty()
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
    val deviceInfoRequest = DeviceInfoManager.getDeviceInfoRequest()
    val service = retrofit().create(ApiService::class.java)
    val keyPair = KeyManager.generateKeyWithPrefix(prefix)
    val user = service.register(
        RegisterRequest(
            username = username,
            accountKey = AccountKey(publicKey = keyPair.public.toFormatString()),
            deviceInfo = deviceInfoRequest
        )
    )
    logd(TAG, user.toString())
    return user
}

fun generatePrefix(text: String): String {
    val timestamp = System.currentTimeMillis().toString()
    val combinedInput = "${text}_$timestamp"
    val bytes = MessageDigest.getInstance(HashAlgorithm.SHA2_256.algorithm)
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
                hashAlgo = cryptoProvider.getHashAlgorithm().index,
                signAlgo = cryptoProvider.getSignatureAlgorithm().index
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
    updateAccountTransferCount(0)
    cleanBackupMnemonicPreference()
    delay(1000)
}

fun clearWebViewCache() {
    WebStorage.getInstance().deleteAllData()
}
