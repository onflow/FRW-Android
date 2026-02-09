package com.flowfoundation.wallet.manager.account

import android.content.Intent
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
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.emoji.model.WalletEmojiInfo
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.walletdata.FlowWallet
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
import kotlinx.serialization.Serializable
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import com.flowfoundation.wallet.utils.setUploadedAddressSet
import kotlinx.coroutines.delay
import com.flowfoundation.wallet.utils.storeWalletPassword

import com.flowfoundation.wallet.manager.walletdata.WalletDataManager

import com.flowfoundation.wallet.manager.walletdata.MainWallet
import com.flowfoundation.wallet.page.restore.keystore.model.KeystoreAddress
import kotlin.text.isNullOrEmpty

object AccountManager {
    private val TAG = AccountManager::class.java.simpleName
    private val accounts = mutableListOf<Account>()
    private var uploadedAddressSet = mutableSetOf<String>()
    private val listeners = CopyOnWriteArrayList<WeakReference<OnAccountUpdate>>()
    private val listListeners = CopyOnWriteArrayList<WeakReference<OnAccountListUpdate>>()
    private val userPrefixes = mutableListOf<UserPrefix>()
    private val switchAccounts = mutableListOf<LocalSwitchAccount>()

    private var currentAccount: Account? = null
    private var isInitialized = false
    private var isInitializing = false

    fun init() {
        synchronized(this) {
            if (isInitialized || isInitializing) {
                logd(TAG, "init() called but already initialized or initializing")
                return
            }
            isInitializing = true
        }

        logd(TAG, "Starting AccountManager initialization")
        accounts.clear()
        userPrefixes.clear()
        switchAccounts.clear()
        currentAccount = null

        ioScope {
            try {
                // Load user prefixes first
                val userPrefixList = UserPrefixCacheManager.read()
                if (!userPrefixList.isNullOrEmpty()) {
                    userPrefixes.addAll(userPrefixList)
                    logd(TAG, "Loaded ${userPrefixes.size} user prefixes from cache")
                }

                // Load accounts
                val accountList = AccountCacheManager.read()
                if (accountList.isNullOrEmpty()) {
                    logd(TAG, "No cached accounts found - user needs to login/restore")
                    synchronized(this@AccountManager) {
                        isInitializing = false
                        isInitialized = true
                    }
                    return@ioScope
                }
                logd(TAG, "Found ${accountList.size} cached accounts")
                accounts.addAll(accountList)

                // Find the active account or use the first one
                val activeAccount = accountList.firstOrNull { it.isActive } ?: accountList.first()
                logd(TAG, "Setting active account: ${activeAccount.userInfo.username}")

                // Ensure only one account is marked as active
                accounts.forEach { it.isActive = (it == activeAccount) }
                currentAccount = activeAccount

                // Initialize uploaded address set
                uploadedAddressSet = getUploadedAddressSet().toMutableSet()

                // Dispatch to listeners
                dispatchListeners(activeAccount)

                synchronized(this@AccountManager) {
                    isInitialized = true
                    isInitializing = false
                }

                logd(TAG, "AccountManager initialization completed successfully")
                // Update Accounts info with WalletDataManager
                WalletDataManager.updateWalletData()

            } catch (e: Exception) {
                loge(TAG, "AccountManager initialization failed: $e")
                ErrorReporter.reportWithMixpanel(AccountError.INIT_FAILED, e)

                synchronized(this@AccountManager) {
                    isInitializing = false
                    // Don't set isInitialized = true on failure
                }

                // Clear potentially corrupted state
                accounts.clear()
                userPrefixes.clear()
                switchAccounts.clear()
                currentAccount = null

                // Initialization failed - user needs to login/restore
            }
        }
    }

    fun getSwitchAccountList(): List<Any> {
        logd(TAG, "getSwitchAccountList() called")
        logd(TAG, "Current accounts: $accounts")
        logd(TAG, "Current switchAccounts: $switchAccounts")

        val list = mutableListOf<Any>()
        list.addAll(accounts)

        // Collect all FlowWallet addresses for the current network from walletNodes
        val currentNetwork = chainNetWorkString()
        val addressSet = accounts.flatMap { account ->
            account.walletNodes.filterIsInstance<FlowWallet>()
                .filter { it.chainIdString == currentNetwork }
                .map { it.address }
        }.toSet()

        logd(TAG, "Address set from accounts (current network): $addressSet")

        val filteredSwitchAccounts = switchAccounts.filter { it.address !in addressSet }
        logd(TAG, "Filtered switch accounts: $filteredSwitchAccounts")

        list.addAll(filteredSwitchAccounts)
        logd(TAG, "Final list size: ${list.size}")
        return list
    }

    fun add(account: Account, uid: String? = null) {
        // Clear WalletManager state before setting new account
        WalletManager.clear()

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
        AccountEmojiManager.init()

        // Trigger WalletDataManager to update the current account's walletNodes
        ioScope {
            WalletDataManager.updateCurrentAccount()
        }
    }

    fun get(): Account? {
        val account = currentAccount
        return account
    }


    fun userInfo(): UserInfoData? {
        return currentAccount?.userInfo
    }

    fun emojiInfoList(): List<WalletEmojiInfo>? {
        return get()?.walletEmojiList
    }

    fun walletNodes(): List<MainWallet>? {
        return get()?.walletNodes
    }

    fun encryptedMnemonic(): String? {
        val account = currentAccount
        if (account == null) {
            logd(TAG, "No active account found")
            return null
        }
        val keyStoreAddress = Gson().fromJson(account.keyStoreInfo, KeystoreAddress::class.java)
        return keyStoreAddress?.encryptedMnemonic
    }

    fun removeCurrentAccount() {
        logd(TAG, "removeCurrentAccount() called - performing comprehensive state reset")
        ioScope {
            val index = list().indexOfFirst { it.isActive }
            if (index < 0) {
                logd(TAG, "No active account found to remove")
                return@ioScope
            }

            logd(TAG, "Removing active account and clearing all related state")

            // Set Firebase to anonymous before clearing state
            setToAnonymous()

            // Clear the account from the list
            val account = accounts.removeAt(index)
            logd(TAG, "Removed account: ${account.userInfo.username}")

            // Clear current account and wallet references
            currentAccount = null
            logd(TAG, "Cleared current account and wallet references")

            // Clear user prefixes
            userPrefixes.removeAll { it.userId == account.wallet?.id}
            UserPrefixCacheManager.cache(UserPrefixes().apply { addAll(userPrefixes) })
            logd(TAG, "Cleared user prefixes")

            // Clear account cache
            AccountCacheManager.cache(Accounts().apply { addAll(accounts) })
            logd(TAG, "Cleared account cache")


            // Clear WalletManager state
            try {
                WalletManager.clear()
                logd(TAG, "Cleared WalletManager state")
            } catch (e: Exception) {
                logd(TAG, "Error clearing WalletManager: ${e.message}")
            }

            // Clear CryptoProviderManager state
            try {
                CryptoProviderManager.clear()
                logd(TAG, "Cleared CryptoProviderManager state")
            } catch (e: Exception) {
                logd(TAG, "Error clearing CryptoProviderManager: ${e.message}")
            }

            try {
                AccountEmojiManager.clear()
                logd(TAG, "Cleared AccountEmojiManager state")
            } catch (e: Exception) {
                logd(TAG, "Error clearing AccountEmojiManager: ${e.message}")
            }

            // Remove only this account's password from the map, preserve others
            try {
                removeAccountFromPasswordMap(account)
                logd(TAG, "Removed wallet password for account: ${account.wallet?.id ?: account.userInfo.username}")
            } catch (e: Exception) {
                logd(TAG, "Error removing account wallet password: ${e.message}")
            }

            // Clear uploaded address set
            uploadedAddressSet.clear()
            setUploadedAddressSet(emptySet())
            logd(TAG, "Cleared uploaded address set")

            uiScope {
                // Clear user cache
                clearUserCache()
                logd(TAG, "Cleared user cache")

                // Navigate to main activity (which should show the get started screen)
                logd(TAG, "Relaunching MainActivity after account reset")
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
                uploadPushToken()
            } catch (e: Exception) {
                loge(TAG, "updateUserInfo error: $e")
                ErrorReporter.reportWithMixpanel(AccountError.UPDATE_USER_INFO_FAILED, e)
            }
        }
    }

    fun updateWalletEmojiInfo(username: String, emojiInfoList: List<WalletEmojiInfo>) {
        list().firstOrNull { it.userInfo.username == username }?.walletEmojiList = emojiInfoList
        AccountCacheManager.cache(Accounts().apply { addAll(accounts) })
    }

    /**
     * Atomically update the current account to avoid race conditions.
     */
    fun updateCurrentAccount(transform: (Account) -> Account) {
        synchronized(this) {
            val current = currentAccount ?: return
            val newAccount = transform(current)

            // Only update if changes were made
            if (newAccount != current) {
                val index = accounts.indexOfFirst { it.userInfo.username == newAccount.userInfo.username }
                if (index != -1) {
                    accounts[index] = newAccount
                    currentAccount = newAccount
                    dispatchListeners(newAccount)
                    AccountCacheManager.cache(Accounts().apply { addAll(accounts) })
                }
            }
        }
    }

    fun updateAccount(account: Account) {
        val index = accounts.indexOfFirst { it.userInfo.username == account.userInfo.username }
        if (index != -1) {
            accounts[index] = account
            if (currentAccount?.userInfo?.username == account.userInfo.username) {
                currentAccount = account
                dispatchListeners(account)
            }
            AccountCacheManager.cache(Accounts().apply { addAll(accounts) })
        }
    }

    fun updateAccountList(accountList: List<Account>) {
        if (accountList.isEmpty()) return

        var hasChanges = false
        accountList.forEach { newAccount ->
            val index = accounts.indexOfFirst { it.userInfo.username == newAccount.userInfo.username }
            if (index != -1) {
                accounts[index] = newAccount
                if (currentAccount?.userInfo?.username == newAccount.userInfo.username) {
                    currentAccount = newAccount
                    dispatchListeners(newAccount)
                }
                hasChanges = true
            }
        }

        if (hasChanges) {
            AccountCacheManager.cache(Accounts().apply { addAll(accounts) })
            dispatchListListeners(accounts)
        }
    }

    fun addListener(callback: OnAccountUpdate) {
        uiScope { listeners.add(WeakReference(callback)) }
    }

    fun addAccountListUpdateListener(callback: OnAccountListUpdate) {
        uiScope { listListeners.add(WeakReference(callback)) }
    }

    private fun dispatchListListeners(accounts: List<Account>) {
        logd(TAG, "dispatchListListeners: size=${accounts.size}")
        uiScope {
            listListeners.removeAll { it.get() == null }
            listListeners.forEach { it.get()?.onAccountListUpdate(accounts) }
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
                try {
                    clearUserCache()
                    // Add a small delay to ensure proper state sync
                    delay(200)
                    MainActivity.relaunch(Env.getApp(), true)
                    logd(TAG, "Successfully navigated to main app for already active account")
                } catch (e: Exception) {
                    logd(TAG, "Error navigating to main app for already active account: ${e.message}")
                    // Try alternative navigation method
                    try {
                        val intent = Intent(Env.getApp(), MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        Env.getApp().startActivity(intent)
                    } catch (fallbackException: Exception) {
                        logd(TAG, "Fallback navigation also failed: ${fallbackException.message}")
                    }
                }
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
                    AccountEmojiManager.init()
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

            val resp = service.login(
                LoginRequest(
                    signature = signature,
                    accountKey = accountKey,
                    deviceInfo = deviceInfoRequest
                )
            )

            if (resp.data?.customToken.isNullOrBlank()) {
                loge(tag = "SWITCH_ACCOUNT", msg = "get customToken failed :: ${resp.data?.customToken}")
                loge(tag = "SWITCH_ACCOUNT", msg = "Response status: ${resp.status}, message: ${resp.message}")
                callback.invoke(false)
            } else {
                firebaseLogin(resp.data.customToken) { isSuccess ->
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

    private fun removeAccountFromPasswordMap(account: Account) {
        val accountId = account.wallet?.id
        if (accountId.isNullOrBlank()) {
            logd(TAG, "Account wallet ID is null or blank, skipping password removal")
            return
        }

        val passwordMap = getPasswordMap()
        if (passwordMap.containsKey(accountId)) {
            passwordMap.remove(accountId)
            storeWalletPassword(Gson().toJson(passwordMap))
            logd(TAG, "Successfully removed password for account ID: $accountId")
        } else {
            logd(TAG, "No password found for account ID: $accountId")
        }
    }

    private fun getPasswordMap(): HashMap<String, String> {
        val pref = runCatching { com.flowfoundation.wallet.utils.readWalletPassword() }.getOrNull()
        return if (pref.isNullOrBlank()) {
            HashMap()
        } else {
            runCatching {
                Gson().fromJson(pref, object : TypeToken<HashMap<String, String>>() {}.type)
                    ?: HashMap<String, String>()
            }.getOrElse { HashMap() }
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
                firebaseLogin(resp.data.customToken) { isSuccess ->
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
    @SerializedName("walletEmojiList")
    var walletEmojiList: List<WalletEmojiInfo>? = null,
    @SerializedName("keyStoreInfo")
    var keyStoreInfo: String? = null,
    @SerializedName("walletNodes")
    var walletNodes: List<MainWallet> = emptyList()
)

fun Account.firstFlowWalletAddress(): String? {
    return this.walletNodes.filterIsInstance<FlowWallet>().firstOrNull{ it.chainIdString.equals(chainNetWorkString(), ignoreCase = true) }?.address
}

fun Account.containsFlowWalletAddress(address: String): Boolean {
    return this.walletNodes.filterIsInstance<FlowWallet>().any { it.address == address }
}

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

interface OnAccountListUpdate {
    fun onAccountListUpdate(accounts: List<Account>)
}


interface OnUserInfoReload {
    fun onUserInfoReload()
}

