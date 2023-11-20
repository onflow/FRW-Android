package io.outblock.lilico.network

import android.widget.Toast
import com.google.common.io.BaseEncoding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.nftco.flow.sdk.DomainTag.normalize
import com.nftco.flow.sdk.bytesToHex
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
import io.outblock.lilico.manager.nft.NftCollectionStateManager
import io.outblock.lilico.manager.staking.StakingManager
import io.outblock.lilico.manager.transaction.TransactionStateManager
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.network.model.AccountKey
import io.outblock.lilico.network.model.LoginRequest
import io.outblock.lilico.network.model.LoginResponse
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
import io.outblock.wallet.KeyManager
import io.outblock.wallet.WalletCoreSigner
import io.outblock.wallet.toFormatString
import kotlinx.coroutines.delay
import org.bouncycastle.util.BigIntegers.asUnsignedByteArray
import java.security.MessageDigest
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
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
    val bytes = MessageDigest.getInstance("SHA-256").digest(combinedInput.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

fun formatPublicKey(publicKey: PublicKey?): String {
    return (publicKey as? ECPublicKey)?.w?.let {
        val bytes = asUnsignedByteArray(it.affineX) + asUnsignedByteArray(it.affineY)
        BaseEncoding.base16().lowerCase().encode(bytes)
    } ?: ""
}

fun formatSignData(text: String, domainTag: ByteArray = normalize("FLOW-V0.0-user")): ByteArray {
    return domainTag + text.encodeToByteArray()
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
    val prefix = AccountManager.get()?.prefix
    val resp: LoginResponse
    if (prefix == null) {
        val wallet = Wallet.store().wallet()
        resp = service.login(
            LoginRequest(
                signature = wallet.sign(
                    getFirebaseJwt()
                ),
                accountKey = AccountKey(publicKey = wallet.getPublicKey(removePrefix = true)),
                deviceInfo = deviceInfoRequest
            )
        )
    } else {
        val publicKey = KeyManager.getPublicKeyByPrefix(prefix)
        val privateKey = KeyManager.getPrivateKeyByPrefix(prefix)
        if (privateKey == null || publicKey == null) {
            toast(msgRes = R.string.resume_login_error, duration = Toast.LENGTH_LONG)
            return
        }
        resp = service.login(
            LoginRequest(
                signature = WalletCoreSigner(privateKey).signAsUser(
                    getFirebaseJwt().encodeToByteArray()
                ).bytesToHex(),
                accountKey = AccountKey(publicKey = publicKey.toFormatString()),
                deviceInfo = deviceInfoRequest
            )
        )
    }
    if (resp.data?.customToken.isNullOrBlank()) {
        toast(msgRes = R.string.resume_login_error, duration = Toast.LENGTH_LONG)
        return
    }
    firebaseLogin(resp.data?.customToken!!) { isSuccess ->
        if (isSuccess) {
            setRegistered()
            if (prefix == null) {
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
    updateAccountTransactionCountLocal(0)
    delay(1000)
}