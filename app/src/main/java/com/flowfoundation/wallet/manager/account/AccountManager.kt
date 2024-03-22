package com.flowfoundation.wallet.manager.account

import android.widget.Toast
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.cache.CacheManager
import com.flowfoundation.wallet.firebase.auth.getFirebaseJwt
import com.flowfoundation.wallet.firebase.auth.isAnonymousSignIn
import com.flowfoundation.wallet.firebase.auth.signInAnonymously
import com.flowfoundation.wallet.firebase.messaging.uploadPushToken
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.clearUserCache
import com.flowfoundation.wallet.network.model.AccountKey
import com.flowfoundation.wallet.network.model.LoginRequest
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.network.model.WalletListData
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.page.walletrestore.firebaseLogin
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.getUploadedAddressSet
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.setRegistered
import com.flowfoundation.wallet.utils.setUploadedAddressSet
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.wallet.Wallet
import io.outblock.wallet.KeyManager

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
        val account: Account? = list().firstOrNull { it.userInfo.username == wallet.username }
        if (account == null) {
            addAccountWithWallet(wallet)
        } else {
            account.wallet = wallet
            accountsCache().cache(Accounts().apply { addAll(accounts) })
            WalletManager.walletUpdate()
            uploadPushToken()
        }
    }

    private fun addAccountWithWallet(wallet: WalletListData) {
        ioScope {
            val service = retrofit().create(ApiService::class.java)
            val userInfo = service.userInfo().data
            add(Account(
                userInfo = userInfo,
                prefix = KeyManager.getCurrentPrefix(),
                wallet = wallet
            ))
            WalletManager.walletUpdate()
            uploadPushToken()
        }
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
        val cryptoProvider = CryptoProviderManager.generateAccountCryptoProvider(account)
        if (cryptoProvider == null) {
            callback(false)
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
            callback(false)
            return
        }
        firebaseLogin(resp.data?.customToken!!) { isSuccess ->
            if (isSuccess) {
                setRegistered()
                if (account.prefix == null) {
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
    var prefix: String? = null
)

class Accounts : ArrayList<Account>()

fun accountsCache(): CacheManager<Accounts> {
    return CacheManager("${"accounts".hashCode()}", Accounts::class.java)
}