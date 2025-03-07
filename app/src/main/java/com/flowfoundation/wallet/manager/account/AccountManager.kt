package com.flowfoundation.wallet.manager.account

import android.widget.Toast
import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.cache.AccountCacheManager
import com.flowfoundation.wallet.cache.CacheManager
import com.flowfoundation.wallet.cache.UserPrefixCacheManager
import com.flowfoundation.wallet.firebase.auth.firebaseUid
import com.flowfoundation.wallet.firebase.auth.getFirebaseJwt
import com.flowfoundation.wallet.firebase.auth.isAnonymousSignIn
import com.flowfoundation.wallet.firebase.auth.signInAnonymously
import com.flowfoundation.wallet.firebase.messaging.uploadPushToken
import com.flowfoundation.wallet.manager.account.model.LocalSwitchAccount
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.emoji.model.WalletEmojiInfo
import com.flowfoundation.wallet.manager.evm.EVMAddressData
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.OtherHostService
import com.flowfoundation.wallet.network.clearUserCache
import com.flowfoundation.wallet.network.model.AccountKey
import com.flowfoundation.wallet.network.model.LoginRequest
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.network.model.WalletListData
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.network.retrofitWithHost
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.page.walletrestore.firebaseLogin
import com.flowfoundation.wallet.utils.DATA_PATH
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.getUploadedAddressSet
import com.flowfoundation.wallet.utils.ioScope
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
import io.outblock.wallet.toFormatString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

object AccountManager {

    private val TAG = AccountManager::class.java.simpleName

    private val accounts = mutableListOf<Account>()
    private var uploadedAddressSet = mutableSetOf<String>()
    private val listeners = CopyOnWriteArrayList<WeakReference<OnUserInfoReload>>()
    private val queryService by lazy {
        retrofitWithHost("https://production.key-indexer.flow.com").create(OtherHostService::class.java)
    }
    private val userPrefixes = mutableListOf<UserPrefix>()
    private val switchAccounts = mutableListOf<LocalSwitchAccount>()

    fun init() {
        accounts.clear()
        userPrefixes.clear()
        switchAccounts.clear()
        ioScope {
            migratePrefixInfo(migrateAccount())?.let {
                accounts.addAll(it)
            }
            initEmojiAndEVMInfo()
        }
        uploadedAddressSet = getUploadedAddressSet().toMutableSet()
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

    private suspend fun migratePrefixInfo(accountList: List<Account>?): List<Account>? {
        return try {
            userPrefixes.addAll(UserPrefixCacheManager.read() ?: emptyList())
            val addressPrefixMap = getAddressPrefixMap()
            accountList?.forEach { account ->
                val userId = account.wallet?.id
                val userPrefixInfo = userPrefixes.find { it.userId == userId }
                if (userPrefixInfo != null) {
                    account.prefix = userPrefixInfo.prefix
                } else {
                    val address = account.wallet?.mainnetWallet()?.address()
                    val prefix = addressPrefixMap[address]
                    if (!prefix.isNullOrEmpty()) {
                        account.prefix = prefix
                        if (!userId.isNullOrEmpty()) {
                            userPrefixes.add(UserPrefix(userId, prefix))
                            UserPrefixCacheManager.cache(UserPrefixes().apply { addAll(userPrefixes) })
                        }
                    }
                }
            }
            getLocalPrefix(accountList, addressPrefixMap)
            getLocalStoredKey(accountList)
            accountList
        } catch (e: Exception) {
            loge(TAG, "Error during migration :: $e")
            accountList
        }
    }

    fun getSwitchAccountList(): List<Any> {
        val list = mutableListOf<Any>()
        list.addAll(accounts)
        val addressSet = accounts.mapNotNull { it.wallet?.walletAddress() }.toSet()
        list.addAll(switchAccounts.filter { it.address !in addressSet })
        return list
    }

    private suspend fun getLocalStoredKey(accountList: List<Account>?): List<LocalSwitchAccount> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LocalSwitchAccount>()
        val uidPublicKeyMap = AccountWalletManager.getUIDPublicKeyMap()
        val jobs = uidPublicKeyMap.map { (uid, publicKey) ->
            async {
                val response = queryService.queryAddress(publicKey)
                response.accounts.firstOrNull()?.let { account ->
                    if (switchAccounts.any { it.address == account.address }) {
                        return@let
                    }
                    if (accountList != null && accountList.any { it.wallet?.mainnetWallet()?.address() == account.address }) {
                        return@let
                    }
                    var count = switchAccounts.size
                    switchAccounts.add(LocalSwitchAccount(
                        username = "Profile ${++count}",
                        address = account.address,
                        userId = uid
                    ))
                }
            }
        }
        jobs.awaitAll()
        return@withContext list
    }

    private fun getLocalPrefix(accountList: List<Account>?, addressPrefixMap: Map<String, String>){
        val prefixesToRemove = accountList?.mapNotNull { it.prefix }.orEmpty()
        val localPrefixMap = addressPrefixMap.filter { (_, prefix) ->  prefix !in prefixesToRemove}
        var count = switchAccounts.size
        switchAccounts.addAll(localPrefixMap.map { (address, prefix) ->
            LocalSwitchAccount(
                username = "Profile ${++count}",
                address = address,
                prefix = prefix
            )
        })
    }

    private suspend fun getAddressPrefixMap(): Map<String, String> = withContext(Dispatchers.IO) {
        val map = mutableMapOf<String, String>()
        val prefixes = KeyManager.getAllAliases().filter {
            it.startsWith(KeyManager.KEYSTORE_ALIAS_PREFIX)
        }.map {
            it.removePrefix(KeyManager.KEYSTORE_ALIAS_PREFIX)
        }
        val jobs = prefixes.map { prefix ->
            async {
                val publicKey = KeyManager.getPublicKeyByPrefix(prefix).toFormatString()
                if (publicKey.isNotEmpty()) {
                    val response = queryService.queryAddress(publicKey)
                    response.accounts.forEach {
                        map[it.address] = prefix
                    }
                }
            }
        }
        jobs.awaitAll()
        return@withContext map
    }

    fun add(account: Account, userId: String? = null) {
        accounts.removeAll { it.userInfo.username == account.userInfo.username }
        accounts.add(account)
        accounts.forEach {
            it.isActive = it == account
        }
        AccountCacheManager.cache(Accounts().apply { addAll(accounts) })
        val prefix = account.prefix
        if (!prefix.isNullOrEmpty() && userId != null) {
            userPrefixes.removeAll { it.userId == userId}
            userPrefixes.add(UserPrefix(userId, prefix))
            UserPrefixCacheManager.cache(UserPrefixes().apply { addAll(userPrefixes) })
        }
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
            val account = accounts.removeAt(index)
            userPrefixes.removeAll { it.userId == account.wallet?.id}
            UserPrefixCacheManager.cache(UserPrefixes().apply { addAll(userPrefixes) })
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
                prefix = getCurrentAccountPrefix(wallet.id),
                wallet = wallet
            ), wallet.id)
            WalletManager.walletUpdate()
            uploadPushToken()
            onUserInfoReload()
        }
    }

    private fun getCurrentAccountPrefix(userId: String?): String {
        return userPrefixes.find { it.userId == userId }?.prefix ?: KeyManager.getCurrentPrefix()
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

    fun switch(switchAccount: LocalSwitchAccount, onFinish: () -> Unit) {
        ioScope {
            if (isSwitching) {
                return@ioScope
            }
            isSwitching = true
            switchAccount(switchAccount) { isSuccess ->
                if (isSuccess) {
                    isSwitching = false
                    switchAccounts.remove(switchAccount)
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

    private suspend fun switchAccount(switchAccount: LocalSwitchAccount, callback: (isSuccess: Boolean) -> Unit) {
        if (!setToAnonymous()) {
            loge(tag = "SWITCH_ACCOUNT", msg = "set to anonymous failed")
            callback.invoke(false)
            return
        }
        val deviceInfoRequest = DeviceInfoManager.getDeviceInfoRequest()
        val service = retrofit().create(ApiService::class.java)
        val cryptoProvider = CryptoProviderManager.getSwitchAccountCryptoProvider(switchAccount)
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
                    if (switchAccount.prefix == null) {
                        Wallet.store().resume()
                    } else {
                        firebaseUid()?.let { userId ->
                            userPrefixes.removeAll { it.userId == userId}
                            userPrefixes.add(UserPrefix(userId, switchAccount.prefix))
                            UserPrefixCacheManager.cache(UserPrefixes().apply { addAll(userPrefixes) })
                        }
                    }
                    callback.invoke(true)
                } else {
                    loge(tag = "SWITCH_ACCOUNT", msg = "get firebase login failed :: ${resp.data.customToken}")
                    callback.invoke(false)
                }
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
        val cryptoProvider = CryptoProviderManager.getSwitchAccountCryptoProvider(account)
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
                    if (account.prefix == null && account.keyStoreInfo == null) {
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

@Serializable
data class UserPrefix(
    @SerializedName("userId")
    val userId: String,
    @SerializedName("prefix")
    var prefix: String
)

class UserPrefixes: ArrayList<UserPrefix>()

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
