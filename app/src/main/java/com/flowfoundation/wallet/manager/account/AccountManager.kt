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
import com.flowfoundation.wallet.manager.flow.FlowCadenceApi
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
import com.google.gson.reflect.TypeToken
import com.nftco.flow.sdk.AddressRegistry
import com.nftco.flow.sdk.FlowAddress
import com.flow.wallet.CryptoProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import com.flowfoundation.wallet.manager.wallet.walletAddress
import com.flowfoundation.wallet.utils.read
import com.flowfoundation.wallet.utils.setUploadedAddressSet
import org.onflow.flow.models.DomainTag
import org.onflow.flow.models.SigningAlgorithm
import org.onflow.flow.models.hexToBytes
import org.onflow.flow.models.toHexString

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
                    // Instead of going to the server, jump straight to the
                    // restore screen (or show the "import keystore" UI).
                    logd(TAG, "No cached account – require keystore/seed restore")
                    return@ioScope
                } else {
                    val account = accountList.first()
                    currentAccount = account
                    var walletRestored = false

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
        val account = currentAccount
        logd(TAG, "get() returning account: $account")
        return account
    }

    fun wallet(): com.flow.wallet.wallet.Wallet? {
        // Ask the source of truth first
        val wmWallet = WalletManager.wallet()
        if (wmWallet != null) return wmWallet

        // fall back to the copy we may have created in init()
        return currentWallet
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
            
            // Check what's actually registered on-chain for this account
            try {
                verifyOnChainAccount(account, cryptoProvider)
            } catch (e: Exception) {
                logd(TAG, "Failed to verify on-chain account: ${e.message}")
            }
            
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
            
            // Validate signature locally for debugging
            validateSignatureLocally(cryptoProvider, jwt, signature)
            
            // Check public key format
            val originalPublicKey = publicKey
            val cleanPublicKey = publicKey.removePrefix("0x")
            logd(TAG, "Public key validation:")
            logd(TAG, "  Original: $originalPublicKey")
            logd(TAG, "  Clean (no 0x): $cleanPublicKey")
            logd(TAG, "  Length: ${cleanPublicKey.length} characters (${cleanPublicKey.length / 2} bytes)")
            
            // For ECDSA_secp256k1, public key should be 64 bytes (128 hex chars) without 0x prefix
            val expectedLength = when (cryptoProvider.getSignatureAlgorithm()) {
                SigningAlgorithm.ECDSA_secp256k1 -> 128
                SigningAlgorithm.ECDSA_P256 -> 128
                else -> -1
            }
            
            if (expectedLength > 0 && cleanPublicKey.length != expectedLength) {
                loge(TAG, "Public key length mismatch: expected $expectedLength, got ${cleanPublicKey.length}")
            }
            
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

    /**
     * Verify what's actually registered on-chain for this account
     */
    private suspend fun verifyOnChainAccount(account: Account, cryptoProvider: CryptoProvider) {
        try {
            logd(TAG, "=== ON-CHAIN ACCOUNT VERIFICATION ===")
            val address = account.wallet?.walletAddress()
            if (address == null) {
                logd(TAG, "No wallet address available for verification")
                return
            }
            
            logd(TAG, "Checking on-chain account for address: $address")
            val onChainAccount = FlowCadenceApi.getAccount(address)
            logd(TAG, "On-chain account address: ${onChainAccount.address}")
            logd(TAG, "On-chain account keys count: ${onChainAccount.keys?.size ?: 0}")
            
            val clientPublicKey = cryptoProvider.getPublicKey().removePrefix("0x").lowercase()
            logd(TAG, "Client public key (clean): $clientPublicKey")
            
            onChainAccount.keys?.forEachIndexed { index, key ->
                logd(TAG, "On-chain key [$index]:")
                logd(TAG, "  Index: ${key.index}")
                logd(TAG, "  Public Key: ${key.publicKey}")
                logd(TAG, "  Signing Algorithm: ${key.signingAlgorithm}")
                logd(TAG, "  Hashing Algorithm: ${key.hashingAlgorithm}")
                logd(TAG, "  Weight: ${key.weight}")
                logd(TAG, "  Sequence Number: ${key.sequenceNumber}")
                logd(TAG, "  Revoked: ${key.revoked}")
                
                val onChainPublicKey = key.publicKey.removePrefix("0x").lowercase()
                val match = onChainPublicKey == clientPublicKey
                logd(TAG, "  Matches client key: $match")
                
                if (match) {
                    logd(TAG, "  ✓ FOUND MATCHING KEY ON-CHAIN!")
                    logd(TAG, "  Checking algorithm consistency:")
                    logd(TAG, "    Client signing algo: ${cryptoProvider.getSignatureAlgorithm()}")
                    logd(TAG, "    On-chain signing algo: ${key.signingAlgorithm}")
                    logd(TAG, "    Client hashing algo: ${cryptoProvider.getHashAlgorithm()}")
                    logd(TAG, "    On-chain hashing algo: ${key.hashingAlgorithm}")
                    
                    if (cryptoProvider.getSignatureAlgorithm() != key.signingAlgorithm) {
                        logd(TAG, "    ⚠️ SIGNING ALGORITHM MISMATCH!")
                    }
                    if (cryptoProvider.getHashAlgorithm() != key.hashingAlgorithm) {
                        logd(TAG, "    ⚠️ HASHING ALGORITHM MISMATCH!")
                    }
                }
            }
            
            val hasMatchingKey = onChainAccount.keys?.any { 
                it.publicKey.removePrefix("0x").lowercase() == clientPublicKey 
            } ?: false
            
            if (!hasMatchingKey) {
                logd(TAG, "❌ NO MATCHING PUBLIC KEY FOUND ON-CHAIN!")
                logd(TAG, "This explains why verification fails on the server.")
            } else {
                logd(TAG, "✓ Matching public key found on-chain")
            }
            
            logd(TAG, "=== END ON-CHAIN ACCOUNT VERIFICATION ===")
        } catch (e: Exception) {
            logd(TAG, "Exception during on-chain verification: ${e.message}")
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

    /**
     * Debug function to validate signature locally before sending to server
     * This helps identify if the issue is with signature generation or server verification
     */
    private fun validateSignatureLocally(cryptoProvider: CryptoProvider, jwt: String, signature: String): Boolean {
        return try {
            logd(TAG, "Validating signature locally...")
            val publicKeyBytes = cryptoProvider.getPublicKey().removePrefix("0x").hexToBytes()
            val domainTag = DomainTag.User.bytes
            val jwtBytes = jwt.encodeToByteArray()
            val messageToVerify = domainTag + jwtBytes
            
            // For local validation, we'd need access to the verification logic
            // This is mainly for debugging and logging purposes
            logd(TAG, "Local signature validation details:")
            logd(TAG, "  Domain tag: ${domainTag.toHexString()}")
            logd(TAG, "  JWT bytes length: ${jwtBytes.size}")
            logd(TAG, "  Message to verify length: ${messageToVerify.size}")
            logd(TAG, "  Public key bytes length: ${publicKeyBytes.size}")
            logd(TAG, "  Signature: $signature")
            logd(TAG, "  Signature bytes length: ${signature.length / 2}")
            
            // We can't easily verify without implementing the full crypto verification
            // but we can check basic properties
            val expectedSignatureLength = when (cryptoProvider.getSignatureAlgorithm()) {
                SigningAlgorithm.ECDSA_P256, SigningAlgorithm.ECDSA_secp256k1 -> 128 // 64 bytes * 2 hex chars
                else -> -1
            }
            
            if (expectedSignatureLength > 0 && signature.length != expectedSignatureLength) {
                loge(TAG, "Signature length mismatch: expected $expectedSignatureLength, got ${signature.length}")
                return false
            }
            
            logd(TAG, "Basic signature validation passed")
            true
        } catch (e: Exception) {
            loge(TAG, "Local signature validation failed: ${e.message}")
            false
        }
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
            
            // Validate signature locally for debugging
            validateSignatureLocally(cryptoProvider, jwt, signature)
            
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

private fun oldAccountsCache(): List<Account> {
    val cacheAccounts = CacheManager<Accounts>("${"accounts".hashCode()}", object : TypeToken<Accounts>() {}.type).read()
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
