package io.outblock.lilico.manager.account

import android.widget.Toast
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.gson.annotations.SerializedName
import com.nftco.flow.sdk.bytesToHex
import io.outblock.lilico.R
import io.outblock.lilico.cache.CacheManager
import io.outblock.lilico.firebase.auth.getFirebaseJwt
import io.outblock.lilico.firebase.auth.isAnonymousSignIn
import io.outblock.lilico.firebase.auth.signInAnonymously
import io.outblock.lilico.firebase.messaging.uploadPushToken
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.network.ApiService
import io.outblock.lilico.network.clearUserCache
import io.outblock.lilico.network.model.AccountKey
import io.outblock.lilico.network.model.LoginRequest
import io.outblock.lilico.network.model.LoginResponse
import io.outblock.lilico.network.model.UserInfoData
import io.outblock.lilico.network.model.WalletListData
import io.outblock.lilico.network.retrofit
import io.outblock.lilico.page.main.MainActivity
import io.outblock.lilico.page.walletrestore.firebaseLogin
import io.outblock.lilico.utils.Env
import io.outblock.lilico.utils.getUploadedAddressSet
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.setRegistered
import io.outblock.lilico.utils.setUploadedAddressSet
import io.outblock.lilico.utils.toast
import io.outblock.lilico.utils.uiScope
import io.outblock.lilico.wallet.Wallet
import io.outblock.lilico.wallet.getPublicKey
import io.outblock.lilico.wallet.sign
import io.outblock.wallet.KeyManager
import io.outblock.wallet.WalletCoreSigner
import io.outblock.wallet.toFormatString

object AccountManager {
    private val accounts = mutableListOf<Account>()
    private var uploadedAddressSet = mutableSetOf<String>()

    fun init() {
        accounts.clear()
        accountsCache().read()?.let { accounts.addAll(it) }
        WalletManager.walletUpdate()
        uploadedAddressSet = getUploadedAddressSet().toMutableSet()
    }

    fun add(account: Account) {
        accounts.removeAll { it.userInfo.username == account.userInfo.username }
        accounts.add(account)
        accounts.forEach {
            it.isActive = it == account
        }
        accountsCache().cache(Accounts().apply { addAll(accounts) })
    }

    fun get(): Account? {
        return accounts.toList().firstOrNull { it.isActive }
    }

    fun userInfo() = get()?.userInfo

    fun updateUserInfo(userInfo: UserInfoData) {
        list().firstOrNull { it.userInfo.username == userInfo.username }?.userInfo = userInfo
        accountsCache().cache(Accounts().apply { addAll(accounts) })
    }


    fun updateWalletInfo(wallet: WalletListData) {
        list().firstOrNull { it.userInfo.username == wallet.username }?.wallet = wallet
        accountsCache().cache(Accounts().apply { addAll(accounts) })
        WalletManager.walletUpdate()
        uploadPushToken()
    }

    fun isAddressUploaded(address: String): Boolean {
        return uploadedAddressSet.contains(address)
    }

    fun addressUploaded(address: String) {
        if (uploadedAddressSet.size >= 20) {
            val oldestAddress = uploadedAddressSet.first()
            uploadedAddressSet.remove(oldestAddress)
        }
        uploadedAddressSet.add(address)
        setUploadedAddressSet(uploadedAddressSet)
    }

    fun list() = accounts.toList()

    fun addressList(): List<String> {
        return accounts.map { it.wallet?.walletAddress() ?: "" }
    }

    var isSwitching = false

    fun switch(account: Account, onFinish: () -> Unit) {
        ioScope {
            if (isSwitching) {
                return@ioScope
            }
            isSwitching = true
            switchAccount(account) { isSuccess ->
                if (isSuccess) {
                    isSwitching = false
                    accounts.forEach {
                        it.isActive = it.userInfo.username == account.userInfo.username
                    }
                    accountsCache().cache(Accounts().apply { addAll(accounts) })
                    uiScope {
                        clearUserCache()
                        MainActivity.relaunch(Env.getApp(), true)
                    }
                } else {
                    isSwitching = false
                    toast(msgRes = R.string.resume_login_error, duration = Toast.LENGTH_LONG)
                }
                onFinish()
            }

        }
    }

    private suspend fun switchAccount(account: Account, callback: (isSuccess: Boolean) -> Unit) {
        if (!setToAnonymous()) {
            callback(false)
            return
        }
        val deviceInfoRequest = DeviceInfoManager.getDeviceInfoRequest()
        val service = retrofit().create(ApiService::class.java)
        val prefix = account.prefix
        val resp: LoginResponse
        if (prefix == null) {
            val wallet = AccountWalletManager.getHDWalletByUID(account.wallet?.id ?: "")
            if (wallet == null) {
                callback(false)
                return
            }
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
            if (publicKey == null || privateKey == null) {
                callback(false)
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
            callback(false)
            return
        }
        firebaseLogin(resp.data?.customToken!!) { isSuccess ->
            if (isSuccess) {
                setRegistered()
                if (prefix == null) {
                    Wallet.store().resume()
                }
                callback(true)
            } else {
                callback(false)
                return@firebaseLogin
            }
        }
    }

    private suspend fun setToAnonymous(): Boolean {
        if (!isAnonymousSignIn()) {
            Firebase.auth.signOut()
            return signInAnonymously()
        }
        return true
    }
}

fun username() = AccountManager.get()!!.userInfo.username

data class Account(
    @SerializedName("username")
    var userInfo: UserInfoData,
    @SerializedName("isActive")
    var isActive: Boolean = false,
    @SerializedName("wallet")
    var wallet: WalletListData? = null,
    @SerializedName("prefix")
    var prefix: String? = null,
)

class Accounts : ArrayList<Account>()

fun accountsCache(): CacheManager<Accounts> {
    return CacheManager("${"accounts".hashCode()}", Accounts::class.java)
}