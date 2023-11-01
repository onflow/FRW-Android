package io.outblock.lilico.manager.account

import android.widget.Toast
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.gson.annotations.SerializedName
import io.outblock.lilico.R
import io.outblock.lilico.cache.CacheManager
import io.outblock.lilico.firebase.auth.getFirebaseJwt
import io.outblock.lilico.firebase.auth.isAnonymousSignIn
import io.outblock.lilico.firebase.auth.signInAnonymously
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.network.ApiService
import io.outblock.lilico.network.clearUserCache
import io.outblock.lilico.network.model.AccountKey
import io.outblock.lilico.network.model.UserInfoData
import io.outblock.lilico.network.model.WalletListData
import io.outblock.lilico.network.retrofit
import io.outblock.lilico.page.main.MainActivity
import io.outblock.lilico.page.walletrestore.firebaseLogin
import io.outblock.lilico.utils.Env
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.setRegistered
import io.outblock.lilico.utils.toast
import io.outblock.lilico.utils.uiScope
import io.outblock.lilico.wallet.Wallet
import io.outblock.lilico.wallet.getPublicKey
import io.outblock.lilico.wallet.sign

object AccountManager {
    private val accounts = mutableListOf<Account>()

    fun init() {
        accounts.clear()
        accountsCache().read()?.let { accounts.addAll(it) }
        WalletManager.walletUpdate()
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
        val wallet = AccountWalletManager.getHDWalletByUID(account.wallet?.id ?: "")
        if (wallet == null) {
            callback(false)
            return
        }
        val deviceInfoRequest = DeviceInfoManager.getDeviceInfoRequest()
        val service = retrofit().create(ApiService::class.java)
        val resp = service.login(
            mapOf(
                "signature" to wallet.sign(
                    getFirebaseJwt()
                ),
                "account_key" to AccountKey(publicKey = wallet.getPublicKey(removePrefix = true)),
                "device_info" to deviceInfoRequest
            )
        )
        if (resp.data?.customToken.isNullOrBlank()) {
            callback(false)
            return
        }
        firebaseLogin(resp.data?.customToken!!) { isSuccess ->
            if (isSuccess) {
                setRegistered()
                Wallet.store().resume()
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
)

class Accounts : ArrayList<Account>()

fun accountsCache(): CacheManager<Accounts> {
    return CacheManager("${"accounts".hashCode()}", Accounts::class.java)
}