package com.flowfoundation.wallet.reactnative.bridge

import android.content.Intent
import android.widget.Toast
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.WritableNativeArray
import com.facebook.react.bridge.WritableNativeMap
import com.flow.wallet.errors.WalletError
import com.flowfoundation.wallet.BuildConfig
import com.flowfoundation.wallet.cache.recentTransactionCache
import com.flowfoundation.wallet.firebase.auth.getFirebaseJwt
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.config.isGasFree
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.evm.EVMWalletManager.isValidEVMAddress
import com.flowfoundation.wallet.manager.evm.EVMWalletManager.toChecksumEVMAddress
import com.flowfoundation.wallet.manager.flowjvm.currentKeyId
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.price.CurrencyManager
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.wallet.walletAddress
import com.flowfoundation.wallet.network.API_HOST
import com.flowfoundation.wallet.network.BASE_HOST
import com.flowfoundation.wallet.page.profile.subpage.currency.model.selectedCurrency
import com.flowfoundation.wallet.page.scan.ScanBarcodeActivity
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.getWatchCollectibleAddress
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.isDev
import com.flowfoundation.wallet.utils.isTesting
import com.flowfoundation.wallet.utils.logToInstabug
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logw
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONObject
import org.onflow.flow.models.FlowAddress
import org.onflow.flow.models.TransactionStatus
import org.onflow.flow.models.hexToBytes
import java.util.Locale

class NativeFRWBridge(reactContext: ReactApplicationContext) : NativeFRWBridgeSpec(reactContext) {

    private val TAG = "NativeFRWBridge"

    init {
        logd(TAG, "NativeFRWBridge initialized with context: ${reactContext != null}")
        logd(TAG, "React context is active: ${reactContext.hasActiveCatalystInstance()}")
    }

    override fun getName(): String {
        logd(TAG, "getName() called, returning: $NAME")
        return NAME
    }

    override fun getSelectedAddress(): String? {
        try {
            val address = WalletManager.selectedWalletAddress()
            logd(TAG, "getSelectedAddress() called, returning: $address")
            return address
        } catch (e: Exception) {
            loge(TAG, "getSelectedAddress() error: ${e.message}")
            return null
        }
    }

    override fun getDebugAddress(): String? {
        try {
            val watchAddress = getWatchCollectibleAddress()
            val resultAddress = watchAddress.ifEmpty {
              null
            }

            logd(TAG, "getDebugAddress() called, watchAddress: '$watchAddress', returning: " +
              "$resultAddress")
            return resultAddress
        } catch (e: Exception) {
            loge(TAG, "getDebugAddress() error: ${e.message}")
            return null
        }
    }

    override fun getNetwork(): String {
        try {
            val network = chainNetWorkString()
            logd(TAG, "getNetwork() called, returning: $network")
            return network
        } catch (e: Exception) {
            loge(TAG, "getNetwork() error: ${e.message}")
            return "mainnet"
        }
    }

    override fun getJWT(promise: Promise) {
        logd(TAG, "getJWT() called")
        ioScope {
            try {
                logd(TAG, "getJWT() - getting Firebase JWT...")
                val jwt = getFirebaseJwt()
                logd(TAG, "getJWT() - JWT obtained successfully")
                uiScope {
                    promise.resolve(jwt)
                }
            } catch (e: Exception) {
                loge(TAG, "getJWT() - error: ${e.message}")
                uiScope {
                    promise.reject("JWT_ERROR", "Failed to get Firebase JWT: ${e.message}", e)
                }
            }
        }
    }

    override fun getVersion(): String {
        return BuildConfig.VERSION_NAME
    }

    override fun getBuildNumber(): String {
        return BuildConfig.VERSION_CODE.toString()
    }

    override fun getLanguage(): String? {
        return Locale.getDefault().language
    }

    override fun sign(hexData: String, promise: Promise) {
        ioScope {
            try {
                val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider() ?: throw WalletError.InitHDWalletFailed
                val signature = cryptoProvider.signData(hexData.hexToBytes())
                if (signature.isNotEmpty()) {
                    uiScope {
                        promise.resolve(signature)
                    }
                } else {
                    uiScope {
                        promise.reject("SIGN_ERROR", "Failed to sign data", null)
                    }
                }
            } catch (e: Exception) {
                uiScope {
                    promise.reject("SIGN_ERROR", "Failed to sign data: ${e.message}", e)
                }
            }
        }
    }

    override fun listenTransaction(txid: String) {
        val transactionState = TransactionState(
            transactionId = txid,
            time = System.currentTimeMillis(),
            state = TransactionStatus.PENDING.ordinal,
            type = TransactionState.TYPE_SEND,
            data = ""
        )
        TransactionStateManager.newTransaction(transactionState)
        uiScope {
            pushBubbleStack(transactionState)
        }
    }

    override fun scanQRCode(promise: Promise) {
        try {
            // Store the promise for later resolution
            QRCodeScanManager.setPendingPromise(promise)

            // Create intent to launch scan activity
            val intent = Intent(reactApplicationContext, ScanBarcodeActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // Start the activity
            reactApplicationContext.startActivity(intent)
        } catch (e: Exception) {
            uiScope {
                promise.reject("SCAN_ERROR", "Failed to start QR scanner: ${e.message}", e)
            }
        }
    }

    override fun getRecentContacts(promise: Promise) {
        ioScope {
            try {
                val recentData = recentTransactionCache().read()?.contacts

                val bridgeContacts = if (!recentData.isNullOrEmpty()) {
                    recentData.map { contact ->
                        RNBridge.Contact(
                            id = contact.id ?: contact.uniqueId(),
                            name = contact.name(),
                            address = contact.address ?: "",
                            avatar = contact.avatar,
                            username = contact.username,
                            contactName = contact.contactName
                        )
                    }
                } else {
                    emptyList()
                }

                val response = RNBridge.RecentContactsResponse(contacts = bridgeContacts)
                val result = bridgeModelToWritableMap(response)

                uiScope {
                    promise.resolve(result)
                }
            } catch (e: Exception) {
                val emptyResponse = RNBridge.RecentContactsResponse(contacts = emptyList())
                val result = bridgeModelToWritableMap(emptyResponse)
                uiScope {
                    promise.resolve(result)
                }
            }
        }
    }

    override fun getWalletAccounts(promise: Promise) {
        ioScope {
            try {
                val bridgeAccounts = mutableListOf<RNBridge.WalletAccount>()

                // Get main wallet address - for hardware-backed keys, wallet() returns null,
                // so we need to use selectedWalletAddress() as fallback
                var mainAddress = WalletManager.wallet()?.walletAddress()
                if (mainAddress.isNullOrEmpty()) {
                    // Hardware-backed key fallback: use the selected address
                    mainAddress = WalletManager.selectedWalletAddress()
                }
                val mainEmojiInfo = createEmojiInfo(mainAddress)
                if (!mainAddress.isNullOrEmpty()) {
                    val mainAccount = RNBridge.WalletAccount(
                        id = "main",
                        name = mainEmojiInfo?.name ?: "Main Account",
                        address = mainAddress,
                        emojiInfo = mainEmojiInfo,
                        parentEmoji = null,
                        parentAddress = null,
                        avatar = null,
                        isActive = isSelectedWalletAddress(mainAddress),
                        type = RNBridge.AccountType.MAIN,
                        balance = null,
                        nfts = null,
                    )
                    bridgeAccounts.add(mainAccount)
                }

                // Get child accounts
                try {
                    val childAccounts = WalletManager.childAccountList(mainAddress)?.get()
                    childAccounts?.forEach { childAccount ->
                        // Debug: Log child account data to see if icon is available
                        println("DEBUG: Child account - name: ${childAccount.name}, icon: ${childAccount.icon}, address: ${childAccount.address}")

                        val childAccountBridge = RNBridge.WalletAccount(
                            id = "child_${childAccount.address}",
                            name = childAccount.name ?: "Child Account",
                            address = childAccount.address,
                            emojiInfo = null,
                            parentEmoji = mainEmojiInfo,
                            parentAddress = mainAddress,
                            avatar = childAccount.icon, // Include the squid avatar!
                            isActive = isSelectedWalletAddress(childAccount.address),
                            type = RNBridge.AccountType.CHILD,
                            balance = null,
                            nfts = null,
                        )
                        bridgeAccounts.add(childAccountBridge)
                    }
                } catch (e: Exception) {
                    // Child accounts might not be available, continue without them
                    println("Child accounts not available: ${e.message}")
                }

                // Get EVM address if available
                try {
                    val evmAddress = EVMWalletManager.getEVMAddress()
                    if (!evmAddress.isNullOrEmpty()) {
                        val evmEmojiInfo = createEmojiInfo(evmAddress)

                        val evmAccount = RNBridge.WalletAccount(
                            id = "evm",
                            name = evmEmojiInfo?.name ?: "EVM Account",
                            address = evmAddress,
                            parentAddress = mainAddress,
                            emojiInfo = evmEmojiInfo,
                            parentEmoji = mainEmojiInfo,
                            avatar = null,
                            isActive = isSelectedWalletAddress(evmAddress),
                            type = RNBridge.AccountType.EVM,
                            balance = null,
                            nfts = null,
                        )
                        bridgeAccounts.add(evmAccount)
                    }
                } catch (e: Exception) {
                    // EVM account might not be available, continue without it
                    println("EVM account not available: ${e.message}")
                }

                val response = RNBridge.WalletAccountsResponse(accounts = bridgeAccounts)
                val result = bridgeModelToWritableMap(response)

                uiScope {
                    promise.resolve(result)
                }
            } catch (e: Exception) {
                val emptyResponse = RNBridge.WalletAccountsResponse(accounts = emptyList())
                val result = bridgeModelToWritableMap(emptyResponse)
                uiScope {
                    promise.resolve(result)
                }
            }
        }
    }

    override fun closeRN(id: String?) {
        try {
            val currentActivity = reactApplicationContext.currentActivity
            if (currentActivity != null && !currentActivity.isFinishing && !currentActivity.isDestroyed) {
                // Use runOnUiThread to ensure activity operations run on main thread
                currentActivity.runOnUiThread {
                    try {
                        if (!currentActivity.isFinishing && !currentActivity.isDestroyed) {
                            // Use finishAndRemoveTask() to completely remove the activity from recents
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                currentActivity.finishAndRemoveTask()
                            } else {
                                currentActivity.finish()
                            }
                        }
                    } catch (e: Exception) {
                        println("Failed to finish activity on UI thread: ${e.message}")
                    }
                }
            } else {
                println("Activity is null, finishing, or destroyed - skipping closeRN")
            }
        } catch (e: Exception) {
            // If finishing activity fails, log error but don't crash
            println("Failed to close React Native activity: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun getSignKeyIndex(): Double {
        return try {
            // Use the same logic as getWalletAccounts() for consistency
            var address = WalletManager.wallet()?.walletAddress()
            if (address.isNullOrEmpty()) {
                // Hardware-backed key fallback: use the selected address
                address = WalletManager.selectedWalletAddress()
            }

            val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()

            if (address.isNullOrEmpty() || cryptoProvider == null) {
                return 0.0
            }

            // This is a synchronous method, but currentKeyId is suspend
            // We need to use a blocking call here since the interface expects a synchronous return
            val keyId = kotlinx.coroutines.runBlocking {
                FlowAddress(address).currentKeyId(cryptoProvider.getPublicKey())
            }

            // Return 0 if no valid key found (-1), otherwise return the key index
            if (keyId == -1) 0.0 else keyId.toDouble()
        } catch (e: Exception) {
            // Return 0 as default key index on any error
            logw(TAG, "getSignKeyIndex() error: ${e.message}")
            0.0
        }
    }

    override fun isFreeGasEnabled(promise: Promise) {
        ioScope {
            try {
                val isFreeGas = isGasFree()
                uiScope {
                    promise.resolve(isFreeGas)
                }
            } catch (e: Exception) {
                uiScope {
                    promise.reject("FREE_GAS_ERROR", "Failed to get free gas status: ${e.message}", e)
                }
            }
        }
    }

    override fun getEnv(): WritableMap {
        val environmentVariables = RNBridge.EnvironmentVariables(
            NODE_API_URL = BASE_HOST,
            GO_API_URL = API_HOST,
            INSTABUG_TOKEN = if (isTesting() || isDev()) {
                BuildConfig.INSTABUG_RN_TOKEN_DEV
            } else {
                BuildConfig.INSTABUG_RN_TOKEN_PROD
            }
        )

        return bridgeModelToWritableMap(environmentVariables)
    }

    override fun getSelectedAccount(promise: Promise) {
        logd(TAG, "getSelectedAccount() called")
        ioScope {
            try {
                logd(TAG, "getSelectedAccount() - getting selected address...")
                val selectedAddress = WalletManager.selectedWalletAddress()
                if (selectedAddress.isNullOrEmpty()) {
                    logw(TAG, "getSelectedAccount() - no selected address found")
                    uiScope {
                        promise.reject("NO_SELECTED_ACCOUNT", "No wallet address selected", null)
                    }
                    return@ioScope
                }
                logd(TAG, "getSelectedAccount() - selected address: $selectedAddress")

                // Determine account type based on address using utility methods
                val mainAddress = WalletManager.wallet()?.walletAddress()

                val accountType = when {
                    EVMWalletManager.isEVMWalletAddress(selectedAddress) -> RNBridge.AccountType.EVM
                    WalletManager.isChildAccount(selectedAddress) -> RNBridge.AccountType.CHILD
                    else -> RNBridge.AccountType.MAIN
                }

                val selectedEmojiInfo = createEmojiInfo(selectedAddress)
                val selectedAccount = RNBridge.WalletAccount(
                    id = "selected",
                    name = selectedEmojiInfo?.name ?: "Selected Account",
                    address = selectedAddress,
                    emojiInfo = selectedEmojiInfo,
                    parentEmoji = if (accountType != RNBridge.AccountType.MAIN) createEmojiInfo(mainAddress) else null,
                    parentAddress = if (accountType != RNBridge.AccountType.MAIN) mainAddress else null,
                    avatar = null,
                    isActive = true,
                    type = accountType,
                    balance = null,
                    nfts = null,
                )

                val result = bridgeModelToWritableMap(selectedAccount)
                logd(TAG, "getSelectedAccount() - account mapped successfully")
                uiScope {
                    promise.resolve(result)
                }
            } catch (e: Exception) {
                loge(TAG, "getSelectedAccount() - error: ${e.message}")
                e.printStackTrace()
                uiScope {
                    promise.reject("SELECTED_ACCOUNT_ERROR", "Failed to get selected account: ${e.message}", e)
                }
            }
        }
    }

    override fun getCurrency(): WritableMap {
        return try {
            val selectedCurrency = selectedCurrency()
            val currentCurrencyPrice = CurrencyManager.currencyPrice()

            val currency = RNBridge.Currency(
                name = selectedCurrency.name,
                symbol = selectedCurrency.symbol,
                rate = currentCurrencyPrice.toString()
            )

            bridgeModelToWritableMap(currency)
        } catch (e: Exception) {
            // Return default USD currency on error
            val defaultCurrency = RNBridge.Currency(
                name = "USD",
                symbol = "$",
                rate = "1.0"
            )
            bridgeModelToWritableMap(defaultCurrency)
        }
    }

    override fun getTokenRate(token: String): String {
        return try {
            val fungibleToken = FungibleTokenListManager.getTokenById(token)
            fungibleToken?.tokenPrice()?.toString() ?: "0.0"
        } catch (e: Exception) {
            // Return "0.0" on error
            "0.0"
        }
    }

    private val gson = Gson()

    // Helper method to convert Bridge models to React Native data
    private fun bridgeModelToWritableMap(model: Any): WritableNativeMap {
        return try {
            val json = gson.toJson(model)
            jsonToWritableMap(json)
        } catch (e: Exception) {
            println("Error converting bridge model to WritableMap: ${e.message}")
            e.printStackTrace()
            WritableNativeMap()
        }
    }

    // Helper method to convert JSON string to WritableMap
    private fun jsonToWritableMap(jsonString: String): WritableNativeMap {
        val map = WritableNativeMap()
        try {
            val jsonObject = JSONObject(jsonString)
            jsonObject.keys().forEach { key ->
                val value = jsonObject.get(key)
                when (value) {
                    is String -> map.putString(key, value)
                    is Boolean -> map.putBoolean(key, value)
                    is Int -> map.putInt(key, value)
                    is Long -> map.putDouble(key, value.toDouble())
                    is Float -> map.putDouble(key, value.toDouble())
                    is Double -> map.putDouble(key, value)
                    is JSONArray -> map.putArray(key, jsonArrayToWritableArray(value))
                    is JSONObject -> map.putMap(key, jsonToWritableMap(value.toString()))
                    JSONObject.NULL -> map.putNull(key)
                    null -> map.putNull(key)
                    else -> {
                        // Handle any other types by converting to string
                        map.putString(key, value.toString())
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    // Helper method to convert JSONArray to WritableArray
    private fun jsonArrayToWritableArray(jsonArray: JSONArray): WritableNativeArray {
        val array = WritableNativeArray()
        try {
            for (i in 0 until jsonArray.length()) {
                val value = jsonArray.get(i)
                when (value) {
                    is String -> array.pushString(value)
                    is Boolean -> array.pushBoolean(value)
                    is Int -> array.pushInt(value)
                    is Long -> array.pushDouble(value.toDouble())
                    is Float -> array.pushDouble(value.toDouble())
                    is Double -> array.pushDouble(value)
                    is JSONObject -> array.pushMap(jsonToWritableMap(value.toString()))
                    is JSONArray -> array.pushArray(jsonArrayToWritableArray(value))
                    JSONObject.NULL -> array.pushNull()
                    null -> array.pushNull()
                    else -> {
                        // Handle any other types by converting to string
                        array.pushString(value.toString())
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return array
    }


    private fun createWalletProfileFromAccount(account: Account): RNBridge.WalletProfile? {
        return try {
            logd(TAG, "createWalletProfileFromAccount() - creating profile for account: ${account.userInfo.username}")

            // Get user info from the specific account (similar to AccountManager.userInfo())
            val userInfo = account.userInfo
            logd(TAG, "createWalletProfileFromAccount() - userInfo: ${userInfo.username}, avatar:" +
              " ${userInfo.avatar}")

            // Get user ID similar to the original implementation
            val userId = account.wallet?.id ?: ""
            logd(TAG, "createWalletProfileFromAccount() - userId: $userId")

            val bridgeAccounts = mutableListOf<RNBridge.WalletAccount>()

            // Get main wallet address from account
            val mainAddress = account.wallet?.walletAddress()
            if (mainAddress.isNullOrEmpty()) {
                logw(TAG, "createWalletProfileFromAccount() - no main address found for account: " +
                  "${account.userInfo.username}")
                return null
            }

            val mainEmojiInfo = createEmojiInfo(mainAddress)
            val mainAccount = RNBridge.WalletAccount(
                id = "main",
                name = mainEmojiInfo?.name ?: "Main Account",
                address = mainAddress,
                emojiInfo = mainEmojiInfo,
                parentEmoji = null,
                parentAddress = null,
                avatar = null,
                isActive = isSelectedWalletAddress(mainAddress),
                type = RNBridge.AccountType.MAIN,
                balance = null,
                nfts = null,
            )
            bridgeAccounts.add(mainAccount)

            // Get child accounts
            try {
                val childAccounts = WalletManager.childAccountList(mainAddress)?.get()
                childAccounts?.forEach { childAccount ->
                    val childAccountBridge = RNBridge.WalletAccount(
                        id = "child_${childAccount.address}",
                        name = childAccount.name,
                        address = childAccount.address,
                        emojiInfo = null,
                        parentEmoji = mainEmojiInfo,
                        parentAddress = mainAddress,
                        avatar = childAccount.icon,
                        isActive = isSelectedWalletAddress(childAccount.address),
                        type = RNBridge.AccountType.CHILD,
                        balance = null,
                        nfts = null,
                    )
                    bridgeAccounts.add(childAccountBridge)
                }
            } catch (e: Exception) {
                logw(TAG, "createWalletProfileFromAccount() - child accounts not available: ${e.message}")
            }

            // Get EVM address if available
            try {
                val evmAddress = if (isSelectedWalletAddress(mainAddress)) {
                    EVMWalletManager.getEVMAddress()
                } else {
                    val address = account.evmAddressData?.evmAddressMap?.get(mainAddress)
                    if (address.isNullOrBlank() || address == "0x") {
                        null
                    } else {
                      val checksumAddress = toChecksumEVMAddress(address)
                      // Validate the address format - if it's corrupted, try to refresh it
                      if (!isValidEVMAddress(checksumAddress)) {
                        logd(TAG, "Detected corrupted EVM address: $checksumAddress, attempting to refresh")
                        return null
                      }
                      checksumAddress
                    }
                }
                if (!evmAddress.isNullOrEmpty()) {
                    val evmEmojiInfo = createEmojiInfo(evmAddress)
                    val evmAccount = RNBridge.WalletAccount(
                        id = "evm",
                        name = evmEmojiInfo?.name ?: "EVM Account",
                        address = evmAddress,
                        parentAddress = mainAddress,
                        emojiInfo = evmEmojiInfo,
                        parentEmoji = mainEmojiInfo,
                        avatar = null,
                        isActive = isSelectedWalletAddress(evmAddress),
                        type = RNBridge.AccountType.EVM,
                        balance = null,
                        nfts = null,
                    )
                    bridgeAccounts.add(evmAccount)
                }
            } catch (e: Exception) {
                logw(TAG, "createWalletProfileFromAccount() - EVM account not available: ${e
                  .message}")
            }

            // Create wallet profile
            RNBridge.WalletProfile(
                name = account.userInfo.nickname,
                avatar = account.userInfo.avatar,
                uid = userId,
                accounts = bridgeAccounts
            )
        } catch (e: Exception) {
            loge(TAG, "createWalletProfileFromAccount() - error creating profile for account: " +
              "${account.userInfo.username}, error: ${e.message}")
            null
        }
    }

    override fun getWalletProfiles(promise: Promise) {
        logd(TAG, "getWalletProfiles() called")
        ioScope {
            try {
                logd(TAG, "getWalletProfiles() - getting all accounts from AccountManager...")

                // Get all accounts from AccountManager
                val accounts = AccountManager.list()
                logd(TAG, "getWalletProfiles() - found ${accounts.size} accounts")

                val profiles = mutableListOf<RNBridge.WalletProfile>()

                // Create wallet profile for each account
                accounts.forEach { account ->
                    createWalletProfileFromAccount(account)?.let { profile ->
                        profiles.add(profile)
                        logd(TAG, "getWalletProfiles() - added profile for account: ${account
                          .userInfo.username}")
                    }
                }

                val response = RNBridge.WalletProfilesResponse(profiles = profiles)
                val result = bridgeModelToWritableMap(response)

                logd(TAG, "getWalletProfiles() - ${profiles.size} profiles mapped successfully")
                uiScope {
                    promise.resolve(result)
                }
            } catch (e: Exception) {
                loge(TAG, "getWalletProfiles() - error: ${e.message}")
                e.printStackTrace()

                // Return empty profiles on error to maintain consistency
                val emptyResponse = RNBridge.WalletProfilesResponse(profiles = emptyList())
                val result = bridgeModelToWritableMap(emptyResponse)
                uiScope {
                    promise.resolve(result)
                }
            }
        }
    }

    // Toast methods
    override fun showToast(title: String, message: String?, type: String?, duration: Double?) {
        try {
            // Concatenate title and message
            val displayMessage = when {
                title.isNotEmpty() && !message.isNullOrEmpty() -> "$title: $message"
                title.isNotEmpty() -> title
                !message.isNullOrEmpty() -> message
                else -> ""
            }
            if (displayMessage.isEmpty()) {
                logw(TAG, "showToast() skipped - empty message")
                return
            }

            val toastDuration = duration ?: 2000.0

            logd(TAG, "showToast() called - title: $title, message: $message, type:" +
              " ${type ?: "info"}, duration: ${toastDuration}ms")

            // Convert duration from milliseconds to boolean (long or short)
            val isLongDuration = toastDuration > 2000.0

            uiScope {
                toast(msg = displayMessage, duration = if (isLongDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT)
            }
        } catch (e: Exception) {
            loge(TAG, "showToast() error: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun hideToast(id: String) {
        try {
            logd(TAG, "hideToast() called - id: $id")
            // Android native toast typically auto-dismiss, but we can implement custom logic here
            // For now, this is mainly for API compatibility
        } catch (e: Exception) {
            loge(TAG, "hideToast() error: ${e.message}")
        }
    }

    override fun clearAllToasts() {
        try {
            logd(TAG, "clearAllToasts() called")
            // Android native toast typically auto-dismiss, but we can implement custom logic here
            // For now, this is mainly for API compatibility
        } catch (e: Exception) {
            loge(TAG, "clearAllToasts() error: ${e.message}")
        }
    }

    override fun createCOAAccount(promise: Promise) {
        logd(TAG, "createCOAAccount() called - Creating Secure Enclave/Hybrid account")
        ioScope {
            try {
                // Auto-generate username (3-15 chars as per server requirement)
                // Use last 8 digits of timestamp to keep it within length limit
                val timestamp = System.currentTimeMillis().toString()
                val username = "u${timestamp.takeLast(8)}"  // e.g., "u12345678" (9 chars)
                logd(TAG, "createCOAAccount() - generated username: $username")
                logd(TAG, "createCOAAccount() - starting account registration...")

                // Use the existing registerOutblock function which creates a COA/Hybrid account:
                // - Generates keys (secure enclave on supported devices)
                // - Registers with backend server
                // - Creates Flow blockchain account
                // - Sets up Firebase authentication
                // - Creates local Account in AccountManager
                val success = com.flowfoundation.wallet.network.registerOutblock(username)

                if (success) {
                    logd(TAG, "createCOAAccount() - account created successfully")

                    // Get the created account details
                    val account = AccountManager.get()
                    val address = WalletManager.selectedWalletAddress()

                    // Retrieve the mnemonic that was generated during registration
                    val mnemonic = try {
                        com.flowfoundation.wallet.wallet.Wallet.store().mnemonic()
                    } catch (e: Exception) {
                        loge(TAG, "Failed to retrieve mnemonic: ${e.message}")
                        null
                    }

                    val phraseWords = mnemonic?.split(" ")

                    // Create success response using WritableMap
                    val response = WritableNativeMap()
                    response.putBoolean("success", true)
                    response.putString("address", address)
                    response.putString("username", account?.userInfo?.username ?: username)
                    response.putString("mnemonic", mnemonic)

                    val phraseArray = WritableNativeArray()
                    phraseWords?.forEach { word -> phraseArray.pushString(word) }
                    response.putArray("phrase", phraseArray)

                    response.putString("accountType", "coa")
                    response.putNull("error")

                    uiScope {
                        promise.resolve(response)
                    }
                } else {
                    loge(TAG, "createCOAAccount() - account creation failed")

                    val response = WritableNativeMap()
                    response.putBoolean("success", false)
                    response.putNull("address")
                    response.putNull("username")
                    response.putNull("mnemonic")
                    response.putNull("phrase")
                    response.putString("accountType", "coa")
                    response.putString("error", "Failed to create account")

                    uiScope {
                        promise.resolve(response)
                    }
                }
            } catch (e: Exception) {
                loge(TAG, "createCOAAccount() - error: ${e.message}")
                e.printStackTrace()

                val response = WritableNativeMap()
                response.putBoolean("success", false)
                response.putNull("address")
                response.putNull("username")
                response.putNull("mnemonic")
                response.putNull("phrase")
                response.putString("accountType", "coa")
                response.putString("error", e.message ?: "Unknown error")

                uiScope {
                    promise.resolve(response)
                }
            }
        }
    }

    override fun saveMnemonic(mnemonic: String, customToken: String, txId: String, promise: Promise) {
        logd(TAG, "saveMnemonic() called - EOA account initialization")
        logd(TAG, "saveMnemonic() - txId: $txId")
        
        ioScope {
            try {
                // Step 8: Securely store the mnemonic
                logd(TAG, "saveMnemonic() - Step 8: Storing mnemonic securely...")
                
                // Store mnemonic in secure storage (similar to registerOutblock's password storage)
                val passwordMap = try {
                    val pref = com.flowfoundation.wallet.utils.readWalletPassword()
                    if (pref.isBlank()) {
                        HashMap<String, String>()
                    } else {
                        Gson().fromJson(pref, object : com.google.gson.reflect.TypeToken<HashMap<String, String>>() {}.type)
                    }
                } catch (e: Exception) {
                    HashMap<String, String>()
                }
                
                // Generate a unique prefix for this EOA account
                val timestamp = System.currentTimeMillis().toString()
                val prefix = com.flowfoundation.wallet.network.generatePrefix("eoa_$timestamp")
                
                // Store mnemonic globally for backup support
                com.flowfoundation.wallet.utils.storeWalletPassword(
                    Gson().toJson(passwordMap.apply { put("global", mnemonic) })
                )
                logd(TAG, "saveMnemonic() - Mnemonic stored with prefix: $prefix")
                
                // Step 9: Firebase authentication with custom token
                logd(TAG, "saveMnemonic() - Step 9: Authenticating with Firebase...")
                var authSuccess = false
                
                // Delete existing Firebase token and user
                com.google.firebase.messaging.FirebaseMessaging.getInstance().deleteToken()
                com.google.firebase.ktx.Firebase.auth.currentUser?.delete()?.addOnCompleteListener {
                    logd(TAG, "saveMnemonic() - Previous Firebase user deleted")
                }
                
                // Sign in with custom token
                com.flowfoundation.wallet.firebase.auth.firebaseCustomLogin(customToken) { isSuccessful, _ ->
                    ioScope {
                        if (isSuccessful) {
                            logd(TAG, "saveMnemonic() - Firebase authentication successful")
                            authSuccess = true
                            
                            // Step 10 & 11: Initialize Wallet-Kit and account discovery
                            try {
                                logd(TAG, "saveMnemonic() - Step 10-11: Initializing Wallet-Kit and discovering account...")
                                
                                // Create SeedPhraseKey from mnemonic using Flow-Wallet-Kit
                                val baseDir = java.io.File(com.flowfoundation.wallet.utils.Env.getApp().filesDir, "wallet")
                                val storage = com.flow.wallet.storage.FileSystemStorage(baseDir)
                                
                                // Create SeedPhraseKey from mnemonic (same pattern as other restore flows)
                                val seedPhraseKey = com.flow.wallet.keys.SeedPhraseKey(
                                    mnemonicString = mnemonic,
                                    passphrase = "",
                                    derivationPath = "m/44'/539'/0'/0/0",
                                    keyPair = null,
                                    storage = storage
                                )
                                
                                // Store the seed phrase key with prefix as ID
                                val keyId = "prefix_key_$prefix"
                                seedPhraseKey.store(keyId, prefix)
                                logd(TAG, "saveMnemonic() - SeedPhraseKey stored with ID: $keyId")
                                
                                // Get the public key
                                val publicKeyBytes = seedPhraseKey.publicKey(org.onflow.flow.models.SigningAlgorithm.ECDSA_P256)
                                if (publicKeyBytes == null) {
                                    throw IllegalStateException("Failed to get public key from seed phrase key")
                                }
                                
                                // Fetch user info and wallet list from backend
                                val service = com.flowfoundation.wallet.network.retrofit().create(com.flowfoundation.wallet.network.ApiService::class.java)
                                val userInfo = service.userInfo().data
                                val walletListData = service.getWalletList().data
                                
                                if (walletListData == null) {
                                    throw IllegalStateException("No wallet data found")
                                }
                                
                                // Initialize Wallet SDK with account from Flow network using txId for fast discovery
                                val walletForSDK = com.flow.wallet.wallet.WalletFactory.createKeyWallet(
                                    seedPhraseKey,
                                    setOf(org.onflow.flow.ChainId.Mainnet, org.onflow.flow.ChainId.Testnet),
                                    storage
                                )
                                
                                // Use txId to fetch account from Flow network
                                walletListData.wallets?.forEach { walletData ->
                                    walletData.blockchain?.forEach { blockchain ->
                                        try {
                                            val chainIdForBlockchain = when (blockchain.chainId.lowercase()) {
                                                "mainnet" -> org.onflow.flow.ChainId.Mainnet
                                                "testnet" -> org.onflow.flow.ChainId.Testnet
                                                else -> null
                                            }
                                            if (chainIdForBlockchain != null && blockchain.address.isNotBlank()) {
                                                val address = if (blockchain.address.startsWith("0x")) {
                                                    blockchain.address
                                                } else {
                                                    "0x${blockchain.address}"
                                                }
                                                logd(TAG, "saveMnemonic() - Fetching account $address using txId: $txId")
                                                walletForSDK.fetchAccountByAddress(address, chainIdForBlockchain)
                                                logd(TAG, "saveMnemonic() - Account fetched successfully")
                                            }
                                        } catch (e: Exception) {
                                            logd(TAG, "saveMnemonic() - Warning: Could not fetch account: ${e.message}")
                                        }
                                    }
                                }
                                
                                // Add account to AccountManager
                                AccountManager.add(
                                    Account(
                                        userInfo = userInfo,
                                        prefix = prefix,
                                        wallet = walletListData
                                    ),
                                    com.flowfoundation.wallet.firebase.auth.firebaseUid()
                                )
                                logd(TAG, "saveMnemonic() - Account added to AccountManager")
                                
                                // Initialize WalletManager
                                WalletManager.init()
                                logd(TAG, "saveMnemonic() - WalletManager initialized")
                                
                                // Get crypto provider
                                val currentAccount = AccountManager.get()
                                val cryptoProvider = CryptoProviderManager.generateAccountCryptoProvider(currentAccount!!)
                                logd(TAG, "saveMnemonic() - Crypto provider generated")
                                
                                // Track account creation
                                // Note: AccountCreateKeyType.SEED_PHRASE may not exist in enum, using KEY_STORE as fallback
                                // The actual key type is tracked via the mnemonic storage
                                com.flowfoundation.wallet.mixpanel.MixpanelManager.accountCreated(
                                    cryptoProvider!!.getPublicKey(),
                                    com.flowfoundation.wallet.mixpanel.AccountCreateKeyType.KEY_STORE,
                                    cryptoProvider.getSignatureAlgorithm().value,
                                    cryptoProvider.getHashAlgorithm().algorithm
                                )
                                
                                // Clear cache
                                com.flowfoundation.wallet.network.clearUserCache()
                                
                                // Return success
                                val response = WritableNativeMap()
                                response.putBoolean("success", true)
                                response.putNull("error")
                                
                                uiScope {
                                    promise.resolve(response)
                                }
                                
                                logd(TAG, "saveMnemonic() - EOA account initialization complete!")
                                
                                // Step 12: Close React Native view (handled by caller)
                                // Step 13: Notification permission (handled by caller)
                                
                            } catch (e: Exception) {
                                loge(TAG, "saveMnemonic() - Wallet initialization error: ${e.message}")
                                e.printStackTrace()
                                
                                val response = WritableNativeMap()
                                response.putBoolean("success", false)
                                response.putString("error", "Wallet initialization failed: ${e.message}")
                                
                                uiScope {
                                    promise.resolve(response)
                                }
                            }
                        } else {
                            loge(TAG, "saveMnemonic() - Firebase authentication failed")
                            
                            val response = WritableNativeMap()
                            response.putBoolean("success", false)
                            response.putString("error", "Firebase authentication failed")
                            
                            uiScope {
                                promise.resolve(response)
                            }
                        }
                    }
                }
                
            } catch (e: Exception) {
                loge(TAG, "saveMnemonic() - error: ${e.message}")
                e.printStackTrace()
                
                val response = WritableNativeMap()
                response.putBoolean("success", false)
                response.putString("error", e.message ?: "Unknown error")
                
                uiScope {
                    promise.resolve(response)
                }
            }
        }
    }

    override fun requestNotificationPermission(promise: Promise) {
        logd(TAG, "requestNotificationPermission() called")
        try {
            val currentActivity = reactApplicationContext.currentActivity

            if (currentActivity == null) {
                loge(TAG, "requestNotificationPermission() - no current activity")
                uiScope {
                    promise.reject("NO_ACTIVITY", "No current activity available")
                }
                return
            }

            // Check Android version
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                logd(TAG, "requestNotificationPermission() - requesting POST_NOTIFICATIONS permission")

                // Request permission directly using PermissionX (skip native activity)
                // Cast to FragmentActivity as required by PermissionX
                val fragmentActivity = currentActivity as? androidx.fragment.app.FragmentActivity
                if (fragmentActivity == null) {
                    logd(TAG, "requestNotificationPermission() - activity is not a FragmentActivity")
                    uiScope {
                        promise.reject("INVALID_ACTIVITY", "Activity is not a FragmentActivity")
                    }
                    return
                }

                // Must run on UI thread
                uiScope {
                    com.permissionx.guolindev.PermissionX.init(fragmentActivity)
                        .permissions(android.Manifest.permission.POST_NOTIFICATIONS)
                        .request { allGranted, _, _ ->
                            logd(TAG, "requestNotificationPermission() - permission result: $allGranted")
                            promise.resolve(allGranted)
                        }
                }
            } else {
                // Notifications are automatically granted on Android < 13
                logd(TAG, "requestNotificationPermission() - Android < 13, permission auto-granted")
                uiScope {
                    promise.resolve(true)
                }
            }
        } catch (e: Exception) {
            loge(TAG, "requestNotificationPermission() - error: ${e.message}")
            e.printStackTrace()
            uiScope {
                promise.reject("PERMISSION_ERROR", "Failed to request notification permission: ${e.message}", e)
            }
        }
    }

    override fun checkNotificationPermission(promise: Promise) {
        android.util.Log.d(TAG, "checkNotificationPermission() called")
        try {
            val isGranted = com.flowfoundation.wallet.utils.isNotificationPermissionGrand(reactApplicationContext)
            android.util.Log.d(TAG, "checkNotificationPermission() - isGranted: $isGranted")

            uiScope {
                promise.resolve(isGranted)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "checkNotificationPermission() - error: ${e.message}")
            e.printStackTrace()
            uiScope {
                promise.reject("PERMISSION_ERROR", "Failed to check notification permission: ${e.message}", e)
            }
        }
    }

    override fun logToNative(level: String, message: String, args: ReadableArray) {
        try {
            // Convert ReadableArray to String array
            val stringArgs = Array(args.size()) { i ->
                args.getString(i) ?: ""
            }

            // Delegate to the centralized Instabug logging system in Log.kt
            logToInstabug(level, message, *stringArgs)
        } catch (e: Exception) {
            // Fallback with just the message if args conversion fails
            logToInstabug(level, message)
        }
    }

    override fun setScreenSecurityLevel(level: String) {
        android.util.Log.d(TAG, "setScreenSecurityLevel() called with level: $level")
        try {
            val currentActivity = reactApplicationContext.currentActivity

            if (currentActivity == null) {
                android.util.Log.w(TAG, "setScreenSecurityLevel() - no current activity")
                return
            }

            currentActivity.runOnUiThread {
                when (level) {
                    "secure" -> {
                        // Prevent screenshots and screen recording
                        currentActivity.window.setFlags(
                            android.view.WindowManager.LayoutParams.FLAG_SECURE,
                            android.view.WindowManager.LayoutParams.FLAG_SECURE
                        )
                        android.util.Log.d(TAG, "setScreenSecurityLevel() - FLAG_SECURE enabled")
                    }
                    "normal" -> {
                        // Allow screenshots again
                        currentActivity.window.clearFlags(
                            android.view.WindowManager.LayoutParams.FLAG_SECURE
                        )
                        android.util.Log.d(TAG, "setScreenSecurityLevel() - FLAG_SECURE disabled")
                    }
                    else -> {
                        android.util.Log.w(TAG, "setScreenSecurityLevel() - unknown level: $level")
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "setScreenSecurityLevel() error: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun launchMultiBackup() {
        android.util.Log.d(TAG, "launchMultiBackup() called")
        try {
            val currentActivity = reactApplicationContext.currentActivity

            if (currentActivity == null) {
                android.util.Log.w(TAG, "launchMultiBackup() - no current activity")
                return
            }

            // First launch WalletBackupActivity (parent) so back navigation works correctly
            com.flowfoundation.wallet.page.backup.WalletBackupActivity.launch(currentActivity, fromRegistration = true)

            // Then immediately launch MultiBackupActivity (Cloud backup: Google Drive, Passkey, Recovery Phrase)
            // When user presses back, they will return to WalletBackupActivity
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                reactApplicationContext.currentActivity?.let { activity ->
                    com.flowfoundation.wallet.page.backup.multibackup.MultiBackupActivity.launch(activity)
                    android.util.Log.d(TAG, "launchMultiBackup() - launched MultiBackupActivity on top of WalletBackupActivity")
                }
            }, 300) // Small delay to ensure WalletBackupActivity is created first
        } catch (e: Exception) {
            android.util.Log.e(TAG, "launchMultiBackup() error: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun launchDeviceBackup() {
        android.util.Log.d(TAG, "launchDeviceBackup() called")
        try {
            val currentActivity = reactApplicationContext.currentActivity

            if (currentActivity == null) {
                android.util.Log.w(TAG, "launchDeviceBackup() - no current activity")
                return
            }

            // First launch WalletBackupActivity (parent) so back navigation works correctly
            com.flowfoundation.wallet.page.backup.WalletBackupActivity.launch(currentActivity, fromRegistration = true)

            // Then immediately launch CreateDeviceBackupActivity (QR code sync between devices)
            // When user presses back, they will return to WalletBackupActivity
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                reactApplicationContext.currentActivity?.let { activity ->
                    com.flowfoundation.wallet.page.backup.device.CreateDeviceBackupActivity.launch(activity)
                    android.util.Log.d(TAG, "launchDeviceBackup() - launched CreateDeviceBackupActivity on top of WalletBackupActivity")
                }
            }, 300) // Small delay to ensure WalletBackupActivity is created first
        } catch (e: Exception) {
            android.util.Log.e(TAG, "launchDeviceBackup() error: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun launchSeedPhraseBackup() {
        android.util.Log.d(TAG, "launchSeedPhraseBackup() called")
        try {
            val currentActivity = reactApplicationContext.currentActivity

            if (currentActivity == null) {
                android.util.Log.w(TAG, "launchSeedPhraseBackup() - no current activity")
                return
            }

            // First launch WalletBackupActivity (parent) so back navigation works correctly
            com.flowfoundation.wallet.page.backup.WalletBackupActivity.launch(currentActivity, fromRegistration = true)

            // Then immediately launch BackupRecoveryPhraseActivity (View/create recovery phrase)
            // When user presses back, they will return to WalletBackupActivity
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                reactApplicationContext.currentActivity?.let { activity ->
                    val intent = com.flowfoundation.wallet.page.backup.BackupRecoveryPhraseActivity.createIntent(activity)
                    activity.startActivity(intent)
                    android.util.Log.d(TAG, "launchSeedPhraseBackup() - launched BackupRecoveryPhraseActivity on top of WalletBackupActivity")
                }
            }, 300) // Small delay to ensure WalletBackupActivity is created first
        } catch (e: Exception) {
            android.util.Log.e(TAG, "launchSeedPhraseBackup() error: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun launchNativeBackupOptions() {
        android.util.Log.d(TAG, "launchNativeBackupOptions() called")
        try {
            val currentActivity = reactApplicationContext.currentActivity

            if (currentActivity == null) {
                android.util.Log.w(TAG, "launchNativeBackupOptions() - no current activity")
                return
            }

            // Launch WalletBackupActivity (Native backup options screen)
            com.flowfoundation.wallet.page.backup.WalletBackupActivity.launch(currentActivity, fromRegistration = true)
            android.util.Log.d(TAG, "launchNativeBackupOptions() - launched WalletBackupActivity")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "launchNativeBackupOptions() error: ${e.message}")
            e.printStackTrace()
        }
    }

    companion object {
        const val NAME = "NativeFRWBridge"
    }
}
