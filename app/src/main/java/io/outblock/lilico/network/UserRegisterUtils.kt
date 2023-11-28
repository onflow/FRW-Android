package io.outblock.lilico.network

import android.widget.Toast
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.nftco.flow.sdk.HashAlgorithm
import io.outblock.lilico.R
import io.outblock.lilico.firebase.auth.firebaseCustomLogin
import io.outblock.lilico.firebase.auth.getFirebaseJwt
import io.outblock.lilico.firebase.auth.isAnonymousSignIn
import io.outblock.lilico.firebase.auth.signInAnonymously
import io.outblock.lilico.manager.account.Account
import io.outblock.lilico.manager.account.AccountManager
import io.outblock.lilico.manager.account.BalanceManager
import io.outblock.lilico.manager.account.DeviceInfoManager
import io.outblock.lilico.manager.coin.FlowCoinListManager
import io.outblock.lilico.manager.coin.TokenStateManager
import io.outblock.lilico.manager.key.CryptoProviderManager
import io.outblock.lilico.manager.nft.NftCollectionStateManager
import io.outblock.lilico.manager.staking.StakingManager
import io.outblock.lilico.manager.transaction.TransactionStateManager
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.network.model.AccountKey
import io.outblock.lilico.network.model.LoginRequest
import io.outblock.lilico.network.model.RegisterRequest
import io.outblock.lilico.network.model.RegisterResponse
import io.outblock.lilico.page.walletrestore.firebaseLogin
import io.outblock.lilico.utils.clearCacheDir
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.logd
import io.outblock.lilico.utils.setMeowDomainClaimed
import io.outblock.lilico.utils.setRegistered
import io.outblock.lilico.utils.toast
import io.outblock.lilico.utils.updateAccountTransactionCountLocal
import io.outblock.lilico.wallet.Wallet
import io.outblock.lilico.wallet.createWalletFromServer
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
                    AccountManager.add(
                        Account(
                            userInfo = service.userInfo().data,
                            prefix = prefix
                        )
                    )
                    AccountManager.updateUserKeyIndex(service.userInfo().data.username, prefix)
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
            if (AccountManager.get()?.prefix == null) {
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
    setMeowDomainClaimed(false)
    TokenStateManager.clear()
    WalletManager.clear()
    NftCollectionStateManager.clear()
    TransactionStateManager.reload()
    FlowCoinListManager.reload()
    BalanceManager.clear()
    StakingManager.clear()
    CryptoProviderManager.clear()
    updateAccountTransactionCountLocal(0)
    delay(1000)
}