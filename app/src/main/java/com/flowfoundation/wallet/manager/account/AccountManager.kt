package com.flowfoundation.wallet.manager.account

import android.widget.Toast
import com.flow.wallet.KeyManager
import com.flow.wallet.toFormatString
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
import com.flowfoundation.wallet.utils.error.AccountError
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.getUploadedAddressSet
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.read
import com.flowfoundation.wallet.utils.setRegistered
import com.flowfoundation.wallet.utils.setUploadedAddressSet
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.wallet.Wallet
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import com.flowfoundation.wallet.utils.Env.getStorage
import com.flowfoundation.wallet.manager.wallet.walletAddress

object AccountManager {
    private val TAG = AccountManager::class.java.simpleName
    private val accounts = mutableListOf<Account>()
    private var uploadedAddressSet = mutableSetOf<String>()
    private val listeners = CopyOnWriteArrayList<WeakReference<OnAccountUpdate>>()
    private val userInfoListeners = CopyOnWriteArrayList<WeakReference<OnUserInfoUpdate>>()
    private val walletDataListeners = CopyOnWriteArrayList<WeakReference<OnWalletDataUpdate>>()
    private val userInfoReloadListeners = CopyOnWriteArrayList<WeakReference<OnUserInfoReload>>()
    private val queryService by lazy {
        retrofitWithHost("https://production.key-indexer.flow.com").create(OtherHostService::class.java)
    }
    private val userPrefixes = mutableListOf<UserPrefix>()
    private val switchAccounts = mutableListOf<LocalSwitchAccount>()

    private var currentAccount: Account? = null
    private var currentWallet: com.flow.wallet.wallet.Wallet? = null

    fun init() {
        accounts.clear()
        userPrefixes.clear()
        switchAccounts.clear()
        ioScope {
            try {
                val accountList = AccountCacheManager.read()
                if (accountList.isNullOrEmpty()) {
                    logd(TAG, "No accounts found in cache, attempting to fetch from server")
                    try {
                        val service = retrofit().create(ApiService::class.java)
                        val userInfo = service.userInfo().data
                        val walletList = service.getWalletList()
                        
                        if (walletList.data != null && !walletList.data?.walletAddress().isNullOrBlank()) {
                            val walletAddress = walletList.data?.walletAddress()
                            logd(TAG, "Fetched wallet address from server: $walletAddress")
                            
                            // Create and add the account
                            val account = Account(
                                userInfo = userInfo,
                                wallet = walletList.data
                            )
                            currentAccount = account
                            accounts.add(account)
                            AccountCacheManager.cache(Accounts().apply { addAll(accounts) })
                            dispatchListeners(account)
                            logd(TAG, "Successfully fetched and cached account from server")
                        } else {
                            logd(TAG, "No wallet data found in server response")
                        }
                    } catch (e: Exception) {
                        loge(TAG, "Failed to fetch account from server: $e")
                        ErrorReporter.reportWithMixpanel(AccountError.INIT_FAILED, e)
                    }
                } else {
                    val account = accountList.first()
                    var walletRestored = false
                    // Try to restore from private key (keyStoreInfo)
                    if (!account.keyStoreInfo.isNullOrBlank()) {
                        logd(TAG, "Restoring wallet from keyStoreInfo (private key)")
                        try {
                            val keystoreAddress = com.google.gson.Gson().fromJson(account.keyStoreInfo, com.flowfoundation.wallet.page.restore.keystore.model.KeystoreAddress::class.java)
                            val privateKey = keystoreAddress.privateKey
                            val key = com.flow.wallet.keys.PrivateKey.create(getStorage()).apply {
                                importPrivateKey(privateKey.toByteArray(), com.flow.wallet.keys.KeyFormat.RAW)
                            }
                            currentWallet = com.flow.wallet.wallet.WalletFactory.createKeyWallet(
                                key,
                                setOf(org.onflow.flow.ChainId.Mainnet, org.onflow.flow.ChainId.Testnet),
                                getStorage()
                            )
                            logd(TAG, "Wallet restored from private key. Address: ${currentWallet?.walletAddress()}")
                            walletRestored = true
                        } catch (e: Exception) {
                            loge(TAG, "Failed to restore wallet from private key: $e")
                        }
                    }
                    // Fallback: Try to restore from private key
                    if (!walletRestored && !account.wallet?.walletAddress().isNullOrBlank()) {
                        logd(TAG, "Attempting fallback: restoring wallet from private key")
                        try {
                            val privateKey = account.wallet?.walletAddress() ?: ""
                            val key = com.flow.wallet.keys.PrivateKey.create(getStorage()).apply {
                                importPrivateKey(privateKey.toByteArray(), com.flow.wallet.keys.KeyFormat.RAW)
                            }
                            currentWallet = com.flow.wallet.wallet.WalletFactory.createKeyWallet(
                                key,
                                setOf(org.onflow.flow.ChainId.Mainnet, org.onflow.flow.ChainId.Testnet),
                                getStorage()
                            )
                            logd(TAG, "Wallet restored from private key. Address: ${currentWallet?.walletAddress()}")
                        } catch (e: Exception) {
                            loge(TAG, "Failed to restore wallet from private key: $e")
                        }
                    }
                    currentAccount = account
                    dispatchListeners(account)
                }
            } catch (e: Exception) {
                loge(TAG, "init error: $e")
                com.flowfoundation.wallet.utils.error.ErrorReporter.reportWithMixpanel(com.flowfoundation.wallet.utils.error.AccountError.INIT_FAILED, e)
            }
        }
        uploadedAddressSet = getUploadedAddressSet().toMutableSet()
    }

    private fun migrateAccount(): List<Account>? {
        val oldAccounts = oldAccountsCache()
        val newAccounts = AccountCacheManager.read()
        if (oldAccounts.isEmpty()) {
            return newAccounts?.toList()
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
                    val address = account.wallet?.walletAddress()
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
            ErrorReporter.reportWithMixpanel(AccountError.MIGRATE_PREFIX_FAILED, e)
            loge(TAG, "Error during migration :: $e")
            accountList
        }
    }

    fun getSwitchAccountList(): List<Any> {
        logd(TAG, "getSwitchAccountList() called")
        logd(TAG, "Current accounts: $accounts")
        logd(TAG, "Current switchAccounts: $switchAccounts")
        
        val list = mutableListOf<Any>()
        list.addAll(accounts)
        val addressSet = accounts.mapNotNull { it.wallet?.walletAddress() }.toSet()
        logd(TAG, "Address set from accounts: $addressSet")
        
        val filteredSwitchAccounts = switchAccounts.filter { it.address !in addressSet }
        logd(TAG, "Filtered switch accounts: $filteredSwitchAccounts")
        
        list.addAll(filteredSwitchAccounts)
        logd(TAG, "Final list size: ${list.size}")
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
                    if (accountList != null && accountList.any { it.wallet?.walletAddress() == account.address }) {
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
                val publicKey = KeyManager.getPublicKeyByPrefix(prefix)
                if (publicKey != null) {
                    val response = queryService.queryAddress(publicKey.toFormatString())
                    response.accounts.forEach {
                        map[it.address] = prefix
                    }
                }
            }
        }
        jobs.awaitAll()
        return@withContext map
    }

    fun add(account: Account, uid: String? = null) {
        logd(TAG, "add() called. Adding account: $account, uid: $uid")
        currentAccount = account
        logd(TAG, "Account added. Current account is now: $currentAccount")
        accounts.removeAll { it.userInfo.username == account.userInfo.username }
        accounts.add(account)
        accounts.forEach {
            it.isActive = it == account
        }
        AccountCacheManager.cache(Accounts().apply { addAll(accounts) })
        val prefix = account.prefix
        if (!prefix.isNullOrEmpty() && uid != null) {
            userPrefixes.removeAll { it.userId == uid}
            userPrefixes.add(UserPrefix(uid, prefix))
            UserPrefixCacheManager.cache(UserPrefixes().apply { addAll(userPrefixes) })
        }
        initEmojiAndEVMInfo()
        logd(TAG, "Account added successfully")
    }

    fun get(): Account? {
        logd(TAG, "get() called. Stack trace: ${Thread.currentThread().stackTrace.joinToString("\n") { it.toString() }}")
        val account = currentAccount
        logd(TAG, "get() returning account: $account")
        return account
    }

    fun wallet(): com.flow.wallet.wallet.Wallet? {
        logd(TAG, "wallet() called. Stack trace: ${Thread.currentThread().stackTrace.joinToString("\n") { it.toString() }}")
        val wallet = currentWallet
        logd(TAG, "wallet() returning wallet: $wallet")
        return wallet
    }

    fun userInfo(): UserInfoData? {
        logd(TAG, "userInfo() called")
        logd(TAG, "Current account: $currentAccount")
        logd(TAG, "Current account userInfo: ${currentAccount?.userInfo}")
        logd(TAG, "Current account wallet address: ${currentAccount?.wallet?.walletAddress()}")
        logd(TAG, "Current account prefix: ${currentAccount?.prefix}")
        logd(TAG, "Current account isActive: ${currentAccount?.isActive}")
        return currentAccount?.userInfo
    }

    fun evmAddressData(): EVMAddressData? {
        logd(TAG, "evmAddressData() called")
        return get()?.evmAddressData
    }

    fun emojiInfoList(): List<WalletEmojiInfo>? {
        logd(TAG, "emojiInfoList() called")
        return get()?.walletEmojiList
    }

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
        ioScope {
            try {
                val account = currentAccount ?: return@ioScope
                account.userInfo = userInfo
                AccountCacheManager.cache(Accounts().apply { addAll(accounts) })
                dispatchUserInfoListeners(userInfo)
            } catch (e: Exception) {
                loge(TAG, "updateUserInfo error: $e")
                ErrorReporter.reportWithMixpanel(AccountError.UPDATE_USER_INFO_FAILED, e)
            }
        }
    }

    fun updateWalletInfo(wallet: WalletListData) {
        ioScope {
            try {
                val account = currentAccount ?: return@ioScope
                account.wallet = wallet
                AccountCacheManager.cache(Accounts().apply { addAll(accounts) })
                WalletManager.walletUpdate()
                uploadPushToken()
                dispatchWalletDataListeners(wallet)
            } catch (e: Exception) {
                loge(TAG, "updateWalletInfo error: $e")
                ErrorReporter.reportWithMixpanel(AccountError.UPDATE_WALLET_INFO_FAILED, e)
            }
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

    fun addListener(callback: OnAccountUpdate) {
        uiScope { listeners.add(WeakReference(callback)) }
    }

    fun addUserInfoListener(callback: OnUserInfoUpdate) {
        uiScope { userInfoListeners.add(WeakReference(callback)) }
    }

    fun addWalletDataListener(callback: OnWalletDataUpdate) {
        uiScope { walletDataListeners.add(WeakReference(callback)) }
    }

    fun addUserInfoReloadListener(callback: OnUserInfoReload) {
        uiScope { userInfoReloadListeners.add(WeakReference(callback)) }
    }

    private fun onUserInfoReload() {
        uiScope {
            userInfoReloadListeners.removeAll { it.get() == null}
            userInfoReloadListeners.forEach {it.get()?.onUserInfoReload()}
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

    fun list(): List<Account> {
        logd(TAG, "list() called. Accounts: $accounts")
        return accounts
    }

    private var isSwitching = false

    fun switch(account: Account, onFinish: () -> Unit) {
        logd(TAG, "switch() called. Switching to account: $account")
        ioScope {
            if (isSwitching) {
                logd(TAG, "Already switching accounts, aborting switch.")
                return@ioScope
            }
            isSwitching = true
            currentAccount = account
            logd(TAG, "Account switched. Current account is now: $currentAccount")
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
                logd(TAG, "switch() completed. Current account: $currentAccount")
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

    private suspend fun switchAccount(account: Account, callback: (isSuccess: Boolean) -> Unit) {
        if (!setToAnonymous()) {
            loge(tag = "SWITCH_ACCOUNT", msg = "set to anonymous failed")
            ErrorReporter.reportWithMixpanel(AccountError.SET_ANONYMOUS_FAILED)
            callback.invoke(false)
            return
        }
        val deviceInfoRequest = DeviceInfoManager.getDeviceInfoRequest()
        val service = retrofit().create(ApiService::class.java)
        val cryptoProvider = CryptoProviderManager.getSwitchAccountCryptoProvider(account)
        if (cryptoProvider == null) {
            loge(tag = "SWITCH_ACCOUNT", msg = "get cryptoProvider failed")
            ErrorReporter.reportWithMixpanel(AccountError.GET_CRYPTO_PROVIDER_FAILED)
            callback.invoke(false)
            return
        }
        val resp = service.login(
            LoginRequest(
                signature = cryptoProvider.getUserSignature(getFirebaseJwt()),
                accountKey = AccountKey(
                    publicKey = cryptoProvider.getPublicKey(),
                    hashAlgo = cryptoProvider.getHashAlgorithm().cadenceIndex,
                    signAlgo = cryptoProvider.getSignatureAlgorithm().cadenceIndex
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

    private suspend fun switchAccount(switchAccount: LocalSwitchAccount, callback: (isSuccess: Boolean) -> Unit) {
        if (!setToAnonymous()) {
            loge(tag = "SWITCH_ACCOUNT", msg = "set to anonymous failed")
            ErrorReporter.reportWithMixpanel(AccountError.SET_ANONYMOUS_FAILED)
            callback.invoke(false)
            return
        }
        val deviceInfoRequest = DeviceInfoManager.getDeviceInfoRequest()
        val service = retrofit().create(ApiService::class.java)
        val cryptoProvider = CryptoProviderManager.getSwitchAccountCryptoProvider(switchAccount)
        if (cryptoProvider == null) {
            loge(tag = "SWITCH_ACCOUNT", msg = "get cryptoProvider failed")
            ErrorReporter.reportWithMixpanel(AccountError.GET_CRYPTO_PROVIDER_FAILED)
            callback.invoke(false)
            return
        }
        val resp = service.login(
            LoginRequest(
                signature = cryptoProvider.getUserSignature(getFirebaseJwt()),
                accountKey = AccountKey(
                    publicKey = cryptoProvider.getPublicKey(),
                    hashAlgo = cryptoProvider.getHashAlgorithm().cadenceIndex,
                    signAlgo = cryptoProvider.getSignatureAlgorithm().cadenceIndex
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

    private suspend fun setToAnonymous(): Boolean {
        if (!isAnonymousSignIn()) {
            Firebase.auth.signOut()
            return signInAnonymously()
        }
        return true
    }

    private fun dispatchListeners(account: Account) {
        logd(TAG, "dispatchListeners: $account")
        uiScope {
            listeners.removeAll { it.get() == null }
            listeners.forEach { it.get()?.onAccountUpdate(account) }
        }
    }

    private fun dispatchUserInfoListeners(userInfo: UserInfoData) {
        logd(TAG, "dispatchUserInfoListeners: $userInfo")
        uiScope {
            userInfoListeners.removeAll { it.get() == null }
            userInfoListeners.forEach { it.get()?.onUserInfoUpdate(userInfo) }
        }
    }

    private fun dispatchWalletDataListeners(wallet: WalletListData) {
        logd(TAG, "dispatchWalletDataListeners: $wallet")
        uiScope {
            walletDataListeners.removeAll { it.get() == null }
            walletDataListeners.forEach { it.get()?.onWalletDataUpdate(wallet) }
        }
    }

    fun clear() {
        currentAccount = null
        currentWallet = null
        accounts.clear()
        userPrefixes.clear()
        switchAccounts.clear()
        AccountCacheManager.cache(emptyList())
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

interface OnAccountUpdate {
    fun onAccountUpdate(account: Account)
}

interface OnUserInfoUpdate {
    fun onUserInfoUpdate(userInfo: UserInfoData)
}

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

private fun accountsCache(): List<Account>? {
    val file = File(DATA_PATH, "${"accounts".hashCode()}")
    val str = file.read()
    if (str.isBlank()) {
        return null
    }

    try {
        return Gson().fromJson(str, Accounts::class.java)?.toList()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}
