package com.flowfoundation.wallet.reactnative.bridge

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.WritableNativeMap
import com.facebook.react.bridge.WritableNativeArray
import com.facebook.react.bridge.WritableMap
import com.flow.wallet.errors.WalletError
import com.flowfoundation.wallet.firebase.auth.getFirebaseJwt
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.wallet.walletAddress
import com.flowfoundation.wallet.BuildConfig
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.cache.recentTransactionCache
import com.flowfoundation.wallet.manager.flowjvm.currentKeyId
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.utils.isDev
import com.flowfoundation.wallet.utils.isTesting
import com.flowfoundation.wallet.network.API_HOST
import com.flowfoundation.wallet.network.BASE_HOST
import com.flowfoundation.wallet.manager.config.isGasFree
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.price.CurrencyManager
import com.flowfoundation.wallet.page.profile.subpage.currency.model.selectedCurrency
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import org.onflow.flow.models.TransactionStatus
import org.onflow.flow.models.hexToBytes
import org.onflow.flow.models.FlowAddress
import android.content.Intent
import android.widget.Toast
import com.flowfoundation.wallet.page.scan.ScanBarcodeActivity
import com.google.gson.Gson
import org.json.JSONObject
import org.json.JSONArray
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.evm.EVMWalletManager.isValidEVMAddress
import com.flowfoundation.wallet.manager.evm.EVMWalletManager.toChecksumEVMAddress
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.getWatchCollectibleAddress
import com.flowfoundation.wallet.utils.logToInstabug
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logw
import java.io.File
import java.io.FileOutputStream
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

    override fun shareQRCode(address: String, qrCodeDataUrl: String, promise: Promise) {
        ioScope {
            try {
                logd(TAG, "shareQRCode() called for address: $address")

                // Decode base64 data URL to bitmap
                val base64String = qrCodeDataUrl.substring(qrCodeDataUrl.indexOf(",") + 1)
                val decodedBytes = android.util.Base64.decode(base64String, android.util.Base64.DEFAULT)
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

                // Save bitmap to cache directory
                val cachePath = File(reactApplicationContext.cacheDir, "images")
                cachePath.mkdirs()
                val file = File(cachePath, "qr_code_${System.currentTimeMillis()}.png")
                val fileOutputStream = FileOutputStream(file)
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fileOutputStream)
                fileOutputStream.close()

                // Get content URI using FileProvider
                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                    reactApplicationContext,
                    "${reactApplicationContext.packageName}.provider",
                    file
                )

                // Create share intent
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_TEXT, "Flow Address: $address")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                // Launch share chooser
                val chooserIntent = Intent.createChooser(shareIntent, "Share QR Code")
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                uiScope {
                    reactApplicationContext.startActivity(chooserIntent)
                    promise.resolve(null)
                }

                logd(TAG, "shareQRCode() - share dialog launched successfully")
            } catch (e: Exception) {
                loge(TAG, "shareQRCode() - error: ${e.message}")
                uiScope {
                    promise.reject("QR_SHARE_ERROR", "Failed to share QR code: ${e.message}", e)
                }
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

    companion object {
        const val NAME = "NativeFRWBridge"
    }
}
