package com.flowfoundation.wallet.manager.account

import android.widget.Toast
import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.cache.AccountCacheManager
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
import com.flowfoundation.wallet.network.clearUserCache
import com.flowfoundation.wallet.network.model.AccountKey
import com.flowfoundation.wallet.network.model.LoginRequest
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.network.model.WalletListData
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.page.walletrestore.firebaseLogin
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.Env.getStorage
import com.flowfoundation.wallet.utils.error.AccountError
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.getUploadedAddressSet
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.setRegistered
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.wallet.Wallet
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import kotlinx.serialization.Serializable
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import com.flowfoundation.wallet.manager.wallet.walletAddress
import com.flowfoundation.wallet.utils.setUploadedAddressSet
import org.onflow.flow.models.hexToBytes

object AccountManager {
    private val TAG = AccountManager::class.java.simpleName
    private val accounts = mutableListOf<Account>()
    private var uploadedAddressSet = mutableSetOf<String>()
    private val listeners = CopyOnWriteArrayList<WeakReference<OnAccountUpdate>>()
    private val userInfoListeners = CopyOnWriteArrayList<WeakReference<OnUserInfoUpdate>>()
    private val walletDataListeners = CopyOnWriteArrayList<WeakReference<OnWalletDataUpdate>>()
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
                    // Instead of going to the server, jump straight to the
                    // restore screen (or show the "import keystore" UI).
                    logd(TAG, "No cached account – require keystore/seed restore")
                    return@ioScope
                } else {
                    val account = accountList.first()
                    currentAccount = account

                    // 1)  If there's no keystore, log once and bail out.
                    val keyStoreJson = account.keyStoreInfo
                    if (keyStoreJson.isNullOrBlank()) {
                        logd(TAG, "Cached account has no keyStoreInfo – wallet cannot be restored")
                        dispatchListeners(account)
                        return@ioScope                      // leave currentWallet == null
                    }

                    try {
                        logd(TAG, "Restoring wallet from cached keyStoreInfo")
                        val ks = Gson().fromJson(keyStoreJson,
                            com.flowfoundation.wallet.page.restore.keystore.model.KeystoreAddress::class.java)

                        val keyBytes = ks.privateKey
                            .removePrefix("0x")
                            .also { require(it.length == 64) { "Private key must be 32-byte hex" } }
                            .hexToBytes()

                        val key = com.flow.wallet.keys.PrivateKey
                            .create(getStorage())
                            .apply { importPrivateKey(keyBytes, com.flow.wallet.keys.KeyFormat.RAW) }

                        currentWallet = com.flow.wallet.wallet.WalletFactory.createKeyWallet(
                            key,
                            setOf(org.onflow.flow.ChainId.Mainnet, org.onflow.flow.ChainId.Testnet),
                            getStorage()
                        )

                        logd(TAG, "Wallet restored, address = ${currentWallet?.walletAddress()}")

                        // 2)  Nudge WalletManager so the app sees the wallet immediately.
                        WalletManager.updateWallet(
                            account.wallet!!
                        )
                    } catch (e: Exception) {
                        logd(TAG, "Failed to restore wallet from cached keyStoreInfo: $e")
                        currentWallet = null
                    }
                    currentAccount = account
                    dispatchListeners(account)
                }
            } catch (e: Exception) {
                loge(TAG, "init error: $e")
                ErrorReporter.reportWithMixpanel(AccountError.INIT_FAILED, e)
            }
        }
        uploadedAddressSet = getUploadedAddressSet().toMutableSet()
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

    fun add(account: Account, uid: String? = null) {
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
    }

    fun get(): Account? {
        val account = currentAccount
        return account
    }

    fun wallet(): com.flow.wallet.wallet.Wallet? {
        val wmWallet = WalletManager.wallet()
        if (wmWallet != null) return wmWallet

        // fall back to the copy we may have created in init()
        return currentWallet
    }

    fun userInfo(): UserInfoData? {
        return currentAccount?.userInfo
    }

    fun evmAddressData(): EVMAddressData? {
        return get()?.evmAddressData
    }

    fun emojiInfoList(): List<WalletEmojiInfo>? {
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

    fun addListener(callback: OnAccountUpdate) {
        uiScope { listeners.add(WeakReference(callback)) }
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
        
        // Check if we're already on this account
        if (account.isActive && currentAccount?.userInfo?.username == account.userInfo.username) {
            logd(TAG, "Account is already active, but still triggering navigation")
            // Instead of early return, still trigger the navigation to main app
            uiScope {
                clearUserCache()
                MainActivity.relaunch(Env.getApp(), true)
            }
            onFinish()
            return
        }
        
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
                    loge(TAG, "Account switch failed, showing error toast")
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
                    loge(TAG, "LocalSwitchAccount switch failed, showing error toast")
                    toast(msgRes = R.string.resume_login_error, duration = Toast.LENGTH_LONG)
                }
                onFinish()
            }
        }
    }

    private suspend fun switchAccount(account: Account, callback: (isSuccess: Boolean) -> Unit) {
        try {
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
            
            // Debug the CryptoProvider being used
            logd(TAG, "CryptoProvider details:")
            logd(TAG, "  Type: ${cryptoProvider.javaClass.simpleName}")
            logd(TAG, "  Public Key: ${cryptoProvider.getPublicKey()}")
            logd(TAG, "  Hash Algorithm: ${cryptoProvider.getHashAlgorithm()}")
            logd(TAG, "  Sign Algorithm: ${cryptoProvider.getSignatureAlgorithm()}")
            logd(TAG, "  Key Weight: ${cryptoProvider.getKeyWeight()}")
            
            // Get JWT with force refresh to avoid token expiration issues
            val jwt = getFirebaseJwt(true)
            logd(TAG, "Retrieved JWT for account switch (length: ${jwt.length})")
            
            val publicKey = cryptoProvider.getPublicKey()
            
            // Debug signature generation step by step
            logd(TAG, "Starting signature generation...")
            logd(TAG, "  JWT (first 50 chars): ${jwt.take(50)}...")
            
            val signature = cryptoProvider.getUserSignature(jwt)
            
            logd(TAG, "Signature generation completed:")
            logd(TAG, "  Generated signature: $signature")
            logd(TAG, "  Signature length: ${signature.length} chars (${signature.length / 2} bytes)")
            
            val accountKey = AccountKey(
                publicKey = publicKey,
                hashAlgo = cryptoProvider.getHashAlgorithm().cadenceIndex,
                signAlgo = cryptoProvider.getSignatureAlgorithm().cadenceIndex
            )
            
            logd(TAG, "Account switch request details:")
            logd(TAG, "  Public Key: $publicKey")
            logd(TAG, "  Hash Algorithm: ${cryptoProvider.getHashAlgorithm()}")
            logd(TAG, "  Sign Algorithm: ${cryptoProvider.getSignatureAlgorithm()}")
            logd(TAG, "  Signature length: ${signature.length}")
            logd(TAG, "  Account: ${account.userInfo.username} (${account.wallet?.walletAddress()})")
            
            val resp = service.login(
                LoginRequest(
                    signature = signature,
                    accountKey = accountKey,
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
        } catch (e: retrofit2.HttpException) {
            loge(tag = "SWITCH_ACCOUNT", msg = "HTTP Exception during account switch: ${e.code()} - ${e.message()}")
            
            // Try to get the response body for more details
            try {
                val errorBody = e.response()?.errorBody()?.string()
                loge(tag = "SWITCH_ACCOUNT", msg = "Server response body: $errorBody")
            } catch (bodyException: Exception) {
                loge(tag = "SWITCH_ACCOUNT", msg = "Could not read error response body: ${bodyException.message}")
            }
            
            if (e.code() == 404) {
                loge(tag = "SWITCH_ACCOUNT", msg = "Server returned 404 - possible signature verification failure")
                logd(TAG, "This might be due to:")
                logd(TAG, "  1. Public key mismatch between client and server")
                logd(TAG, "  2. Signature verification failure on server side")
                logd(TAG, "  3. JWT token issues or expiration")
                logd(TAG, "  4. Account not found on server")
            }
            
            loge(tag = "SWITCH_ACCOUNT", msg = "Invoking callback with isSuccess=false due to HTTP ${e.code()}")
            callback.invoke(false)
        } catch (e: Exception) {
            loge(tag = "SWITCH_ACCOUNT", msg = "Exception during account switch: ${e.message}")
            logd(TAG, "Account switch exception: ${e.stackTraceToString()}")
            callback.invoke(false)
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

    private suspend fun switchAccount(switchAccount: LocalSwitchAccount, callback: (isSuccess: Boolean) -> Unit) {
        try {
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
            
            // Debug the CryptoProvider being used
            logd(TAG, "CryptoProvider details:")
            logd(TAG, "  Type: ${cryptoProvider.javaClass.simpleName}")
            logd(TAG, "  Public Key: ${cryptoProvider.getPublicKey()}")
            logd(TAG, "  Hash Algorithm: ${cryptoProvider.getHashAlgorithm()}")
            logd(TAG, "  Sign Algorithm: ${cryptoProvider.getSignatureAlgorithm()}")
            logd(TAG, "  Key Weight: ${cryptoProvider.getKeyWeight()}")
            
            // Get JWT with force refresh to avoid token expiration issues
            val jwt = getFirebaseJwt(true)
            logd(TAG, "Retrieved JWT for local account switch (length: ${jwt.length})")
            
            val publicKey = cryptoProvider.getPublicKey()
            
            // Debug signature generation step by step
            logd(TAG, "Starting signature generation...")
            logd(TAG, "  JWT (first 50 chars): ${jwt.take(50)}...")
            
            val signature = cryptoProvider.getUserSignature(jwt)
            
            logd(TAG, "Signature generation completed:")
            logd(TAG, "  Generated signature: $signature")
            logd(TAG, "  Signature length: ${signature.length} chars (${signature.length / 2} bytes)")
            
            val accountKey = AccountKey(
                publicKey = publicKey,
                hashAlgo = cryptoProvider.getHashAlgorithm().cadenceIndex,
                signAlgo = cryptoProvider.getSignatureAlgorithm().cadenceIndex
            )
            
            logd(TAG, "Local account switch request details:")
            logd(TAG, "  Public Key: $publicKey")
            logd(TAG, "  Hash Algorithm: ${cryptoProvider.getHashAlgorithm()}")
            logd(TAG, "  Sign Algorithm: ${cryptoProvider.getSignatureAlgorithm()}")
            logd(TAG, "  Signature length: ${signature.length}")
            logd(TAG, "  Account: ${switchAccount.username} (${switchAccount.address})")
            
            val resp = service.login(
                LoginRequest(
                    signature = signature,
                    accountKey = accountKey,
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
        } catch (e: retrofit2.HttpException) {
            loge(tag = "SWITCH_ACCOUNT", msg = "HTTP Exception during LocalSwitchAccount switch: ${e.code()} - ${e.message()}")
            
            // Try to get the response body for more details
            try {
                val errorBody = e.response()?.errorBody()?.string()
                loge(tag = "SWITCH_ACCOUNT", msg = "Server response body: $errorBody")
            } catch (bodyException: Exception) {
                loge(tag = "SWITCH_ACCOUNT", msg = "Could not read error response body: ${bodyException.message}")
            }
            
            if (e.code() == 404) {
                loge(tag = "SWITCH_ACCOUNT", msg = "Server returned 404 - possible signature verification failure")
                logd(TAG, "This might be due to:")
                logd(TAG, "  1. Public key mismatch between client and server")
                logd(TAG, "  2. Signature verification failure on server side")
                logd(TAG, "  3. JWT token issues or expiration")
                logd(TAG, "  4. Account not found on server")
            }
            
            loge(tag = "SWITCH_ACCOUNT", msg = "Invoking callback with isSuccess=false due to HTTP ${e.code()}")
            callback.invoke(false)
        } catch (e: Exception) {
            loge(tag = "SWITCH_ACCOUNT", msg = "Exception during LocalSwitchAccount switch: ${e.message}")
            logd(TAG, "LocalSwitchAccount switch exception: ${e.stackTraceToString()}")
            callback.invoke(false)
        }
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

