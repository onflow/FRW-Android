package com.flowfoundation.wallet.manager.account

import android.widget.Toast
import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.cache.AccountCacheManager
import com.flowfoundation.wallet.cache.CacheManager
import com.flowfoundation.wallet.firebase.auth.getFirebaseJwt
import com.flowfoundation.wallet.firebase.auth.isAnonymousSignIn
import com.flowfoundation.wallet.firebase.auth.signInAnonymously
import com.flowfoundation.wallet.firebase.messaging.uploadPushToken
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.emoji.model.WalletEmojiInfo
import com.flowfoundation.wallet.manager.evm.EVMAddressData
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
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
import com.flowfoundation.wallet.utils.DATA_PATH
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.getUploadedAddressSet
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.read
import com.flowfoundation.wallet.utils.setRegistered
import com.flowfoundation.wallet.utils.setUploadedAddressSet
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.wallet.Wallet
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import io.outblock.wallet.KeyManager
import kotlinx.serialization.Serializable
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

object AccountManager {

    private val accounts = mutableListOf<Account>()
    private var uploadedAddressSet = mutableSetOf<String>()
    private val listeners = CopyOnWriteArrayList<WeakReference<OnUserInfoReload>>()

    fun init() {
        accounts.clear()
        migrateAccount()?.let {
            accounts.addAll(it)
        }
        WalletManager.walletUpdate()
        uploadedAddressSet = getUploadedAddressSet().toMutableSet()
        initEmojiAndEVMInfo()
    }

    private fun migrateAccount(): List<Account>? {
        val oldAccounts = oldAccountsCache()
        val newAccounts = AccountCacheManager.read()
        if (oldAccounts.isEmpty()) {
            return newAccounts
        }
        if (newAccounts == null) {
            return oldAccounts
        }
        val migrateMap = oldAccounts.associateBy { it.userInfo.username }.toMutableMap()

        newAccounts.forEach { newAccount ->
            val oldAccount = migrateMap[newAccount.userInfo.username]
            if (oldAccount != null) {
                val prefix = if (oldAccount.prefix.isNullOrBlank()) {
                    newAccount.prefix
                } else {
                    oldAccount.prefix
                }
                migrateMap[newAccount.userInfo.username] = newAccount.copy(prefix = prefix)
            } else {
                migrateMap[newAccount.userInfo.username] = newAccount
            }
        }

        return migrateMap.values.toList()
    }

    fun add(account: Account) {
        accounts.removeAll { it.userInfo.username == account.userInfo.username }
        accounts.add(account)
        accounts.forEach {
            it.isActive = it == account
        }
        AccountCacheManager.cache(Accounts().apply { addAll(accounts) })
        initEmojiAndEVMInfo()
    }

    fun get(): Account? {
        return accounts.toList().firstOrNull { it.isActive }
    }

    fun userInfo() = get()?.userInfo

    fun evmAddressData() = get()?.evmAddressData

    fun emojiInfoList() = get()?.walletEmojiList

    private fun initEmojiAndEVMInfo() {
        EVMWalletManager.init()
        AccountEmojiManager.init()
    }

    fun removeCurrentAccount() {
        ioScope {
            val index = list().indexOfFirst { it.isActive }
            if (index < 0) {
                return@ioScope
            }
            setToAnonymous()
            accounts.removeAt(index)
            AccountCacheManager.cache(Accounts().apply { addAll(accounts) })
            uiScope {
                clearUserCache()
                MainActivity.relaunch(Env.getApp(), true)
            }
        }
    }

    fun updateUserInfo(userInfo: UserInfoData) {
        list().firstOrNull { it.userInfo.username == userInfo.username }?.userInfo = userInfo
        AccountCacheManager.cache(Accounts().apply { addAll(accounts) })
    }

    fun updateWalletInfo(wallet: WalletListData) {
        val account: Account? = list().firstOrNull { it.userInfo.username == wallet.username }
        if (account == null) {
            addAccountWithWallet(wallet)
        } else {
            account.wallet = wallet
            AccountCacheManager.cache(Accounts().apply { addAll(accounts) })
            WalletManager.walletUpdate()
            uploadPushToken()
        }
    }

    fun updateEVMAddressInfo(evmAddressMap: Map<String, String>) {
        get()?.let {
            it.evmAddressData = EVMAddressData(evmAddressMap)
            AccountCacheManager.cache(Accounts().apply { addAll(accounts) })
        }
    }

    fun updateWalletEmojiInfo(username: String, emojiInfoList: List<WalletEmojiInfo>) {
        list().firstOrNull { it.userInfo.username == username }?.walletEmojiList = emojiInfoList
        AccountCacheManager.cache(Accounts().apply { addAll(accounts) })
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
            onUserInfoReload()
        }
    }

    fun addListener(callback: OnUserInfoReload) {
        uiScope { this.listeners.add(WeakReference(callback)) }
    }

    private fun onUserInfoReload() {
        uiScope {
            listeners.removeAll { it.get() == null}
            listeners.forEach {it.get()?.onUserInfoReload()}
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

    private var isSwitching = false

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
                    AccountCacheManager.cache(Accounts().apply { addAll(accounts) })
                    initEmojiAndEVMInfo()
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
            loge(tag = "SWITCH_ACCOUNT", msg = "set to anonymous failed")
            callback.invoke(false)
            return
        }
        val deviceInfoRequest = DeviceInfoManager.getDeviceInfoRequest()
        val service = retrofit().create(ApiService::class.java)
        val cryptoProvider = CryptoProviderManager.generateAccountCryptoProvider(account, true)
        if (cryptoProvider == null) {
            loge(tag = "SWITCH_ACCOUNT", msg = "get cryptoProvider failed")
            callback.invoke(false)
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
            loge(tag = "SWITCH_ACCOUNT", msg = "get customToken failed :: ${resp.data?.customToken}")
            callback.invoke(false)
        } else {
            firebaseLogin(resp.data?.customToken!!) { isSuccess ->
                if (isSuccess) {
                    setRegistered()
                    if (account.prefix == null) {
                        Wallet.store().resume()
                    }
                    callback.invoke(true)
                } else {
                    loge(tag = "SWITCH_ACCOUNT", msg = "get firebase login failed :: ${resp.data.customToken}")
                    callback.invoke(false)
                }
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

@Serializable
data class Account(
    @SerializedName("username")
    var userInfo: UserInfoData,
    @SerializedName("isActive")
    var isActive: Boolean = false,
    @SerializedName("wallet")
    var wallet: WalletListData? = null,
    @SerializedName("prefix")
    var prefix: String? = null,
    @SerializedName("evmAddressData")
    var evmAddressData: EVMAddressData? = null,
    @SerializedName("walletEmojiList")
    var walletEmojiList: List<WalletEmojiInfo>? = null,
    @SerializedName("keyStoreInfo")
    var keyStoreInfo: String? = null
)

class Accounts : ArrayList<Account>()

interface OnUserInfoReload {
    fun onUserInfoReload()
}

private fun oldAccountsCache(): List<Account> {
    val cacheAccounts = CacheManager("${"accounts".hashCode()}", Accounts::class.java).read()
    val accounts = mutableListOf<Account>()
    cacheAccounts?.let {
        accounts.addAll(it)
    }
    accountsCache()?.let {
        accounts.addAll(it)
    }
    return accounts
}

private fun accountsCache(): Accounts? {
    val file = File(DATA_PATH, "${"accounts".hashCode()}")
    val str = file.read()
    if (str.isBlank()) {
        return null
    }

    try {
        return Gson().fromJson(str, Accounts::class.java)
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}
