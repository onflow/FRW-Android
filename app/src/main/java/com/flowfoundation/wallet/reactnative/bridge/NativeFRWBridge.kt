package com.flowfoundation.wallet.reactnative.bridge

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableNativeMap
import com.facebook.react.bridge.WritableNativeArray
import com.facebook.react.bridge.WritableMap
import com.flowfoundation.wallet.firebase.auth.getFirebaseJwt
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.app.ActivityManager
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import org.onflow.flow.models.TransactionStatus
import com.google.gson.Gson
import org.json.JSONObject
import org.json.JSONArray
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.firstFlowWalletAddress
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logw
import wallet.core.jni.HDWallet
import com.flowfoundation.wallet.wallet.Wallet
import com.flowfoundation.wallet.manager.key.HDWalletCryptoProvider
import com.flow.wallet.keys.SeedPhraseKey
import org.onflow.flow.models.SigningAlgorithm
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.DomainTag
import com.flowfoundation.wallet.firebase.auth.firebaseUid
import com.flowfoundation.wallet.utils.Env.getStorage
import androidx.core.content.edit
import com.flow.wallet.KeyManager
import com.flow.wallet.toFormatString
import com.flowfoundation.wallet.manager.flowjvm.CadenceScript
import com.flowfoundation.wallet.manager.flowjvm.transactionByMainWallet
import com.flowfoundation.wallet.manager.transaction.TransactionStateWatcher
import com.flowfoundation.wallet.manager.transaction.isExecuteFinished
import com.flowfoundation.wallet.manager.transaction.isFailed
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.network.model.AccountSignRequest
import com.flowfoundation.wallet.network.model.AccountKeySignature
import com.flowfoundation.wallet.network.model.AccountKey
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.generatePrefix
import org.onflow.flow.infrastructure.Cadence.Companion.uint8
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.flowfoundation.wallet.manager.key.storage.KeyStorageManager
import com.flowfoundation.wallet.reactnative.bridge.handlers.AccountBridgeHandler
import com.flowfoundation.wallet.reactnative.bridge.handlers.AuthBridgeHandler
import com.flowfoundation.wallet.reactnative.bridge.handlers.UIBridgeHandler
import com.flowfoundation.wallet.reactnative.bridge.handlers.UtilsBridgeHandler
import com.flowfoundation.wallet.reactnative.bridge.handlers.WalletBridgeHandler
import com.flowfoundation.wallet.wallet.DERIVATION_PATH

class NativeFRWBridge(reactContext: ReactApplicationContext) : NativeFRWBridgeSpec(reactContext) {

    private val TAG = "NativeFRWBridge"

    // Delegate handlers for different bridge functionality domains
    private val utilsHandler = UtilsBridgeHandler(reactContext)
    private val accountHandler = AccountBridgeHandler(reactContext)
    private val authHandler = AuthBridgeHandler(reactContext)
    private val uiHandler = UIBridgeHandler(reactContext)
    private val walletHandler = WalletBridgeHandler(reactContext)

    /**
     * Send an event to React Native JavaScript
     * @param eventName The name of the event
     * @param params The parameters to send with the event
     */
    fun sendEvent(eventName: String, params: WritableMap?) {
        try {
            reactApplicationContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit(eventName, params)
        } catch (e: Exception) {
            logd(TAG, "Failed to send event $eventName: ${e.message}")
        }
    }

    init {
        ActivityManager.setReactContext(reactContext)
        System.loadLibrary("TrustWalletCore")
    }

    override fun getName(): String = utilsHandler.getName()

    override fun getSelectedAddress(): String? = accountHandler.getSelectedAddress()

    override fun getDebugAddress(): String? = accountHandler.getDebugAddress()

    override fun getNetwork(): String = utilsHandler.getNetwork()

    override fun getJWT(promise: Promise) = authHandler.getJWT(promise)

    override fun getVersion(): String = utilsHandler.getVersion()

    override fun getBuildNumber(): String = utilsHandler.getBuildNumber()

    override fun getLanguage(): String? = utilsHandler.getLanguage()

    override fun getDeviceId(): String = utilsHandler.getDeviceId()

    override fun sign(hexData: String, promise: Promise) = walletHandler.sign(hexData, promise)

    override fun createSeedKey(strength: Double, promise: Promise) {
        logd(TAG, "createSeedKey() called - strength: $strength")
        try {
            val mnemonicStrength = strength.toInt()
            val hdWallet = HDWallet(mnemonicStrength, "")
            val mnemonic = hdWallet.mnemonic()

            // Derive key using P256k1/SHA2_256 to match Flow defaults
            val seedPhraseKey = SeedPhraseKey(
              mnemonicString = mnemonic,
              passphrase = "",
              derivationPath = DERIVATION_PATH,
              storage = getStorage()
            )
            val cryptoProvider = HDWalletCryptoProvider(seedPhraseKey)
            val publicKey = cryptoProvider.getPublicKey()

            val flowKey = WritableNativeMap().apply {
                putString("publicKey", publicKey)
                putInt("signAlgo", cryptoProvider.getSignatureAlgorithm().cadenceIndex)
                putInt("hashAlgo", cryptoProvider.getHashAlgorithm().cadenceIndex)
                putInt("weight", 1000)
                putString("signAlgoString", cryptoProvider.getSignatureAlgorithm().value)
                putString("hashAlgoString", cryptoProvider.getHashAlgorithm().value)
            }

            val result = WritableNativeMap().apply {
                putString("seedphrase", mnemonic)
                putMap("flowKey", flowKey)
            }

            promise.resolve(result)
        } catch (e: Exception) {
            promise.reject("CREATE_KEY_ERROR", e.message, e)
        }
    }

    override fun saveNewKey(key: ReadableMap, promise: Promise) {
        logd(TAG, "saveNewKey() called")
        ioScope {
            try {
                val seedPhrase = key.getString("seedphrase")
                if (seedPhrase.isNullOrEmpty()) {
                    throw IllegalArgumentException("Seed phrase is empty")
                }

                // Update and store the mnemonic in Wallet
                Wallet.store().updateMnemonic(seedPhrase).store()

                // Also persist in independent key storage so it survives account-cache loss
                val uid = firebaseUid() ?: AccountManager.get()?.wallet?.id
                if (!uid.isNullOrBlank()) {
                    KeyStorageManager.saveSeedPhrase(uid, seedPhrase)
                    val address = AccountManager.get()?.firstFlowWalletAddress()
                    if (!address.isNullOrBlank()) {
                        KeyStorageManager.saveWalletAddress(uid, address)
                    }
                }

                logd(TAG, "saveNewKey() - Seed phrase saved successfully")
                uiScope {
                    promise.resolve(null)
                }
            } catch (e: Exception) {
                loge(TAG, "saveNewKey() error: ${e.message}")
                uiScope {
                    promise.reject("SAVE_KEY_ERROR", e.message, e)
                }
            }
        }
    }

    override fun removeOldKey(address: String, publicKey: String, promise: Promise) {
        logd(TAG, "removeOldKey() called - address: $address, publicKey: $publicKey")
        ioScope {
            try {
                val currentProvider = CryptoProviderManager.getCurrentCryptoProvider() ?: throw IllegalStateException("No active crypto provider found")
                if (currentProvider.getPublicKey() != publicKey) {
                    logd(TAG, "removeOldKey() - Public key does not match current provider")
                    return@ioScope
                }
                val account = AccountManager.get() ?: throw IllegalStateException("No active account found")
                val uid = firebaseUid() ?: account.wallet?.id
                val keystoreInfo = account.keyStoreInfo

                // 1. Backup if exists
                if (!keystoreInfo.isNullOrBlank()) {
                    logd(TAG, "Backing up keystore info for user: $uid")
                    val prefs = Env.getApp().getSharedPreferences("backup_keystore", android.content.Context.MODE_PRIVATE)
                    prefs.edit { putString("backup_info_$uid", keystoreInfo) }
                } else {
                    logd(TAG, "No keystore info to backup")
                }

                // 2. Remove keystore info from account
                account.keyStoreInfo = null

                // Update AccountManager cache
                // AccountManager.add(account) will update the list and cache it
                AccountManager.add(account)

                // Delete any pkStorage entry that KeyStorageMigration (Case 2) may have created
                if (!uid.isNullOrBlank()) {
                    KeyStorageManager.deletePrivateKey(uid)
                }

                // 3. Clear CryptoProvider
                CryptoProviderManager.clear()

                // 4. Force reload to verify
                val newProvider = CryptoProviderManager.getCurrentCryptoProvider()
                if (newProvider is HDWalletCryptoProvider) {
                    logd(TAG, "Successfully switched to HDWalletCryptoProvider")
                } else {
                    logw(TAG, "Provider is not HDWalletCryptoProvider after rotation: ${newProvider?.javaClass?.simpleName}")
                }

                // Mark as rotated in BloctoDetector cache to prevent re-triggering
                try {
                    val bloctoPrefs = Env.getApp().getSharedPreferences("blocto_detector_cache", 0)
                    val cacheKey = "blocto.detector.false.${address.lowercase()}"
                    bloctoPrefs.edit { putBoolean(cacheKey, true) }
                    logd(TAG, "Marked address $address as rotated in BloctoDetector cache")
                } catch (e: Exception) {
                    loge(TAG, "Failed to update BloctoDetector cache: ${e.message}")
                }

                uiScope { promise.resolve(null) }
            } catch (e: Exception) {
                loge(TAG, "removeOldKey() error: ${e.message}")
                uiScope { promise.reject("REMOVE_OLD_KEY_ERROR", e.message, e) }
            }
        }
    }

    override fun signRotationRequest(address: String, signatureData: String, promise: Promise) {
        logd(TAG, "signRotationRequest() called - address: $address, signatureData: $signatureData")
        ioScope {
            try {
                val currentAddress = WalletManager.getCurrentFlowWalletAddress() ?: ""
                if (currentAddress != address) {
                    throw IllegalArgumentException("Address mismatch: expected $address, got $currentAddress")
                }

                val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()
                    ?: throw IllegalStateException("No crypto provider found")

                // Add User Domain Tag (FLOW-V0.0-USER)
                val dataToSign = DomainTag.User.bytes + signatureData.encodeToByteArray()
                val signature = cryptoProvider.signData(dataToSign)

                val model = RNBridge.AccountKeySignature(
                    public_key = cryptoProvider.getPublicKey(),
                    hash_algo = cryptoProvider.getHashAlgorithm().cadenceIndex,
                    sign_algo = cryptoProvider.getSignatureAlgorithm().cadenceIndex,
                    signature = signature,
                    sign_message = signatureData,
                    weight = cryptoProvider.getKeyWeight(),
                )

                uiScope { promise.resolve(bridgeModelToWritableMap(model)) }
            } catch (e: Exception) {
                loge(TAG, "signRotationRequest() error: ${e.message}")
                uiScope { promise.reject("SIGN_ROTATION_ERROR", e.message, e) }
            }
        }
    }

    override fun keystoreMigration(promise: Promise) {
        logd(TAG, "keystoreMigration() called")
        ioScope {
            try {
                val account = AccountManager.get() ?: throw IllegalStateException("No active account")
                val username = account.userInfo.username

                // 1. Generate new prefix
                val newPrefix = generatePrefix(username)
                logd(TAG, "keystoreMigration() - generated new prefix: $newPrefix")

                // 2. Generate new Key in Android Keystore
                val keyPair = KeyManager.generateKeyWithPrefix(newPrefix)
                val publicKeyStr = keyPair.public.toFormatString()
                logd(TAG, "keystoreMigration() - generated new public key: $publicKeyStr")

                val txId = CadenceScript.CADENCE_ADD_PUBLIC_KEY.transactionByMainWallet {
                    arg { string(publicKeyStr) }
                    arg { uint8(SigningAlgorithm.ECDSA_P256.cadenceIndex.toUByte()) }
                    arg { uint8(HashingAlgorithm.SHA2_256.cadenceIndex.toUByte()) }
                    arg { ufix64Safe(1000) }
                }

                if (txId != null) {
                    logd(TAG, "Transaction created successfully: $txId")
                    val transactionState = TransactionState(
                        transactionId = txId,
                        time = System.currentTimeMillis(),
                        state = TransactionStatus.PENDING.ordinal,
                        type = TransactionState.TYPE_ADD_PUBLIC_KEY,
                        data = ""
                    )
                    TransactionStateManager.newTransaction(transactionState)
                    pushBubbleStack(transactionState)
                    TransactionStateWatcher(txId).watch { result ->
                        logd(TAG, "watch transaction ${result.status}, ${result.execution}, " +
                          "${result.errorMessage}, ${result.isExecuteFinished()}")
                        when {
                            result.isExecuteFinished() -> {
                                logd(TAG, "Transaction $txId finished successfully")
                                syncKeystoreInfo(account, newPrefix, publicKeyStr, promise)
                            }
                            result.isFailed() -> {
                                logd(TAG, "Transaction $txId failed")
                                throw RuntimeException("Failed to create add public key transaction")
                            }
                        }
                    }
                } else {
                    logd(TAG, "Failed to create transaction - txId is null")
                    throw RuntimeException("Failed to create add public key transaction")
                }
            } catch (e: Exception) {
                loge(TAG, "keystoreMigration() failed: ${e.message}")
                e.printStackTrace()
                uiScope {
                    promise.reject("MIGRATION_ERROR", e.message, e)
                }
            }
        }
    }

    private fun syncKeystoreInfo(account: Account, newPrefix: String, publicKeyStr: String, promise: Promise) {
        ioScope {
            try {
                // 3. Prepare signAccount request
                val service = retrofit().create(ApiService::class.java)
                val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider() ?: throw IllegalStateException("No crypto provider available")

                val jwt = getFirebaseJwt()
                val signature = cryptoProvider.getUserSignature(jwt)

                // Construct the signature object for the CURRENT key
                val currentKeySignature = AccountKeySignature(
                    publicKey = cryptoProvider.getPublicKey(),
                    signMessage = jwt,
                    signature = signature,
                    weight = cryptoProvider.getKeyWeight(),
                    hashAlgo = cryptoProvider.getHashAlgorithm().cadenceIndex,
                    signAlgo = cryptoProvider.getSignatureAlgorithm().cadenceIndex
                )

                // Construct the request for the NEW key
                val request = AccountSignRequest(
                    accountKey = AccountKey(
                        publicKey = publicKeyStr,
                        hashAlgo = HashingAlgorithm.SHA2_256.cadenceIndex, // SHA2_256
                        signAlgo = SigningAlgorithm.ECDSA_P256.cadenceIndex, // NIST_P256
                        weight = 1000
                    ),
                    deviceInfo = listOf(currentKeySignature)
                )

                logd(TAG, "keystoreMigration() - sending signAccount request")
                val response = service.signAccount(request)

                if (response.status > 400) {
                    logd(TAG, "keystoreMigration() - failed with status ${response.status}: ${response.message}")
                    throw IllegalStateException("Sign account failed: ${response.message}")
                }
                logd(TAG, "keystoreMigration() - signAccount success")

                // 4. Update local account state with new prefix
                logd(TAG, "keystoreMigration() - updating local account state")
                val uid = firebaseUid() ?: account.wallet?.id
                val oldPrefix = account.prefix
                account.prefix = newPrefix
                AccountManager.add(account)

                // Sync new prefix to independent AKP storage
                if (!uid.isNullOrBlank()) {
                    KeyStorageManager.saveAndroidKeystorePrefix(uid, newPrefix)
                    val address = account.firstFlowWalletAddress()
                    if (!address.isNullOrBlank()) {
                        KeyStorageManager.saveWalletAddress(uid, address)
                    }
                }

                // Remove the stale file-private-key entry that KeyCompatibilityManager may find
                if (!oldPrefix.isNullOrBlank()) {
                    try {
                        getStorage().remove("prefix_key_$oldPrefix")
                        logd(TAG, "Removed old prefix key from shared storage: prefix_key_$oldPrefix")
                    } catch (e: Exception) {
                        loge(TAG, "Failed to remove old prefix key: ${e.message}")
                    }
                }

                // 5. Reload CryptoProvider
                logd(TAG, "keystoreMigration() - reloading crypto provider")
                CryptoProviderManager.clear()
                // Force reload of provider to ensure it uses the new key
                val newProvider = CryptoProviderManager.getCurrentCryptoProvider()
                if (newProvider != null) {
                    logd(TAG, "keystoreMigration() - new crypto provider loaded successfully")
                } else {
                    logw(TAG, "keystoreMigration() - failed to load new crypto provider")
                }

                // 6. Close RN screen
                uiScope {
                    closeRN(null)
                    promise.resolve(null)
                }
            } catch (e: Exception) {
                loge(TAG, "syncKeystoreInfo() failed: ${e.message}")
                e.printStackTrace()
                promise.reject("MIGRATION_ERROR", e.message, e)
            }
        }
    }

    override fun ethSign(hexData: String?, promise: Promise?) = walletHandler.ethSign(hexData, promise)

    override fun listenTransaction(txid: String) = walletHandler.listenTransaction(txid)

    override fun scanQRCode(promise: Promise) = uiHandler.scanQRCode(promise)

    override fun getRecentContacts(promise: Promise) = accountHandler.getRecentContacts(promise, ::bridgeModelToWritableMap)

    override fun getWalletAccounts(promise: Promise) = accountHandler.getWalletAccounts(promise, ::bridgeModelToWritableMap)

    override fun closeRN(id: String?) = uiHandler.closeRN(id)

    override fun closeRNWithNFT(id: String?) {}

    override fun getSignKeyIndex(): Double = accountHandler.getSignKeyIndex()

    override fun isFreeGasEnabled(promise: Promise) = utilsHandler.isFreeGasEnabled(promise)

    override fun getEnv(): WritableMap = utilsHandler.getEnv(::bridgeModelToWritableMap)

    override fun getSelectedAccount(promise: Promise) = accountHandler.getSelectedAccount(promise, ::bridgeModelToWritableMap)

    override fun getMigrationAssets(
      sourceAddress: String?,
      promise: Promise?
    ) {}

    override fun refreshCoaAfterMigration(promise: Promise?) {}

    override fun getCurrency(): WritableMap = utilsHandler.getCurrency(::bridgeModelToWritableMap)

    override fun getTokenRate(token: String): String = utilsHandler.getTokenRate(token)

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
                when (val value = jsonObject.get(key)) {
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
              when (val value = jsonArray.get(i)) {
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


    override fun getWalletProfiles(promise: Promise) = accountHandler.getWalletProfiles(promise, ::bridgeModelToWritableMap)

    override fun getRecoverableProfiles(promise: Promise) = accountHandler.getRecoverableProfiles(promise, ::bridgeModelToWritableMap)

    override fun switchToProfile(userId: String, promise: Promise) = accountHandler.switchToProfile(userId, promise)

    override fun showToast(title: String, message: String?, type: String?, duration: Double?) = uiHandler.showToast(title, message, type, duration)

    override fun hideToast(id: String) = uiHandler.hideToast(id)

    override fun clearAllToasts() = uiHandler.clearAllToasts()

    override fun registerSecureTypeAccount(username: String, promise: Promise) = authHandler.registerSecureTypeAccount(username, promise, ::sendEvent)

    override fun initSecureEnclaveWallet(txId: String, promise: Promise) = authHandler.initSecureEnclaveWallet(txId, promise)

    override fun generateSeedPhrase(strength: Double?, promise: Promise) = authHandler.generateSeedPhrase(strength, promise, ::bridgeModelToWritableMap)

    override fun getV4RegistrationSignatures(mnemonic: String, promise: Promise) = authHandler.getV4RegistrationSignatures(mnemonic, promise)

    override fun signInWithCustomToken(customToken: String, promise: Promise) = authHandler.signInWithCustomToken(customToken, promise)

    override fun saveMnemonic(mnemonic: String, customToken: String, txId: String, username: String, evmAddress: String?, promise: Promise) = authHandler.saveMnemonic(mnemonic, customToken, txId, username, evmAddress, promise, ::sendEvent)

    override fun requestNotificationPermission(promise: Promise) = utilsHandler.requestNotificationPermission(promise)

    override fun checkNotificationPermission(promise: Promise) = utilsHandler.checkNotificationPermission(promise)

    override fun logToNative(level: String, message: String, args: ReadableArray) = utilsHandler.logToNative(level, message, args)

    override fun setScreenSecurityLevel(level: String) = utilsHandler.setScreenSecurityLevel(level)

    override fun launchNativeScreen(screenName: String, params: String?) = uiHandler.launchNativeScreen(screenName, params)

    companion object {
        const val NAME = "NativeFRWBridge"
    }
}
