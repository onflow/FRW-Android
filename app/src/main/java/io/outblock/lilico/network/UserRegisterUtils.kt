package io.outblock.lilico.network

import android.widget.Toast
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import io.outblock.lilico.R
import io.outblock.lilico.firebase.auth.firebaseCustomLogin
import io.outblock.lilico.firebase.auth.getFirebaseJwt
import io.outblock.lilico.firebase.auth.isAnonymousSignIn
import io.outblock.lilico.firebase.auth.signInAnonymously
import io.outblock.lilico.firebase.messaging.uploadPushToken
import io.outblock.lilico.manager.account.Account
import io.outblock.lilico.manager.account.AccountManager
import io.outblock.lilico.manager.account.BalanceManager
import io.outblock.lilico.manager.account.DeviceInfoManager
import io.outblock.lilico.manager.coin.FlowCoinListManager
import io.outblock.lilico.manager.coin.TokenStateManager
import io.outblock.lilico.manager.nft.NftCollectionStateManager
import io.outblock.lilico.manager.staking.StakingManager
import io.outblock.lilico.manager.transaction.TransactionStateManager
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.network.model.AccountKey
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
import io.outblock.lilico.wallet.getPublicKey
import io.outblock.lilico.wallet.sign
import kotlinx.coroutines.delay
import wallet.core.jni.HDWallet
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


private const val TAG = "UserRegisterUtils"

// register one step, create user & create wallet
suspend fun registerOutblock(
    username: String,
) = suspendCoroutine { continuation ->
    ioScope {
        registerOutblockUserInternal(username) { isSuccess, wallet ->
            ioScope {
                if (isSuccess) {
                    uploadPushToken()
                    createWalletFromServer()
                    Wallet.store().reset(wallet.mnemonic())
                    setRegistered()

                    val service = retrofit().create(ApiService::class.java)
                    AccountManager.add(Account(userInfo = service.userInfo().data))
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
    callback: (isSuccess: Boolean, wallet: HDWallet) -> Unit,
) {
    val wallet = HDWallet(128, "")
    if (!setToAnonymous()) {
        resumeAccount()
        callback.invoke(false, wallet)
        return
    }

    val user = registerServer(username, wallet)

    if (user.status > 400) {
        callback(false, wallet)
        return
    }

    logd(TAG, "start delete user")
    registerFirebase(user) { isSuccess ->
        callback.invoke(isSuccess, wallet)
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

private suspend fun registerServer(username: String, wallet: HDWallet): RegisterResponse {
    val deviceInfoRequest = DeviceInfoManager.getDeviceInfoRequest()
    val service = retrofit().create(ApiService::class.java)
    val user = service.register(
        RegisterRequest(
            username = username,
            accountKey = AccountKey(publicKey = wallet.getPublicKey(removePrefix = true)),
            deviceInfo = deviceInfoRequest

        )
    )
    logd(TAG, user.toString())
    return user
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
    val wallet = Wallet.store().wallet()
    val service = retrofit().create(ApiService::class.java)
    val resp = service.login(mapOf(
        "signature" to wallet.sign(
            getFirebaseJwt()
        ),
        "account_key" to AccountKey(publicKey = wallet.getPublicKey(removePrefix = true)),
        "device_info" to deviceInfoRequest
    ))
    if (resp.data?.customToken.isNullOrBlank()) {
        toast(msgRes = R.string.resume_login_error, duration = Toast.LENGTH_LONG)
        return
    }
    firebaseLogin(resp.data?.customToken!!) { isSuccess ->
        if (isSuccess) {
            setRegistered()
            Wallet.store().resume()
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
    updateAccountTransactionCountLocal(0)
    delay(1000)
}