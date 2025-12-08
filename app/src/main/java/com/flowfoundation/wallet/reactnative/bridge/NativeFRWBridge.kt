package com.flowfoundation.wallet.reactnative.bridge

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.WritableNativeArray
import com.facebook.react.bridge.WritableNativeMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.flowfoundation.wallet.reactnative.bridge.handlers.AccountBridgeHandler
import com.flowfoundation.wallet.reactnative.bridge.handlers.AuthBridgeHandler
import com.flowfoundation.wallet.reactnative.bridge.handlers.UIBridgeHandler
import com.flowfoundation.wallet.reactnative.bridge.handlers.UtilsBridgeHandler
import com.flowfoundation.wallet.reactnative.bridge.handlers.WalletBridgeHandler
import com.flowfoundation.wallet.utils.logd
import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONObject

/**
 * Helper to convert string screen name to NativeScreenName enum.
 * Uses Gson SerializedName annotations for mapping.
 */
private fun nativeScreenFromString(screenName: String): RNBridge.NativeScreenName? {
    return RNBridge.NativeScreenName.entries.find { enumValue ->
        val serializedName = enumValue.javaClass.getField(enumValue.name)
            .getAnnotation(com.google.gson.annotations.SerializedName::class.java)
            ?.value
        serializedName == screenName
    }
}

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
        logd(TAG, "NativeFRWBridge initialized with context: ${reactContext != null}")
        logd(TAG, "React context is active: ${reactContext.hasActiveCatalystInstance()}")
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

    override fun ethSign(hexData: String?, promise: Promise?) = walletHandler.ethSign(hexData, promise)

    override fun listenTransaction(txid: String) = walletHandler.listenTransaction(txid)

    override fun scanQRCode(promise: Promise) = uiHandler.scanQRCode(promise)

    override fun getRecentContacts(promise: Promise) = accountHandler.getRecentContacts(promise, ::bridgeModelToWritableMap)

    override fun getWalletAccounts(promise: Promise) = accountHandler.getWalletAccounts(promise, ::bridgeModelToWritableMap)

    override fun closeRN(id: String?) = uiHandler.closeRN(id)

    override fun getSignKeyIndex(): Double = accountHandler.getSignKeyIndex()

    override fun isFreeGasEnabled(promise: Promise) = utilsHandler.isFreeGasEnabled(promise)

    override fun getEnv(): WritableMap = utilsHandler.getEnv(::bridgeModelToWritableMap)

    override fun getSelectedAccount(promise: Promise) = accountHandler.getSelectedAccount(promise, ::bridgeModelToWritableMap)

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


    override fun getWalletProfiles(promise: Promise) = accountHandler.getWalletProfiles(promise, ::bridgeModelToWritableMap)

    override fun showToast(title: String, message: String?, type: String?, duration: Double?) = uiHandler.showToast(title, message, type, duration)

    override fun hideToast(id: String) = uiHandler.hideToast(id)

    override fun clearAllToasts() = uiHandler.clearAllToasts()

    override fun registerSecureTypeAccount(username: String, promise: Promise) = authHandler.registerSecureTypeAccount(username, promise, ::sendEvent)

    override fun generateSeedPhrase(strength: Double?, promise: Promise) = authHandler.generateSeedPhrase(strength, promise, ::bridgeModelToWritableMap)

    override fun signInWithCustomToken(customToken: String, promise: Promise) = authHandler.signInWithCustomToken(customToken, promise)

    override fun saveMnemonic(mnemonic: String, customToken: String, txId: String, username: String, promise: Promise) = authHandler.saveMnemonic(mnemonic, customToken, username, promise, ::sendEvent)

    override fun requestNotificationPermission(promise: Promise) = utilsHandler.requestNotificationPermission(promise)

    override fun checkNotificationPermission(promise: Promise) = utilsHandler.checkNotificationPermission(promise)

    override fun logToNative(level: String, message: String, args: ReadableArray) = utilsHandler.logToNative(level, message, args)

    override fun setScreenSecurityLevel(level: String) = utilsHandler.setScreenSecurityLevel(level)

    override fun launchNativeScreen(screenName: String, params: String?) = uiHandler.launchNativeScreen(screenName, params)

    override fun launchNativeScreen(screenName: String) {
        logd(TAG, "launchNativeScreen() called - screen: $screenName")

        try {
            val currentActivity = reactApplicationContext.currentActivity
            if (currentActivity == null) {
                logw(TAG, "launchNativeScreen() - no current activity")
                return
            }

            val screen = nativeScreenFromString(screenName)
            if (screen == null) {
                loge(TAG, "launchNativeScreen() - unknown screen: $screenName")
                return
            }

            val intent = when (screen) {
                RNBridge.NativeScreenName.DEVICE_BACKUP -> {
                    Intent(currentActivity, com.flowfoundation.wallet.page.wallet.sync.WalletSyncActivity::class.java)
                }
                RNBridge.NativeScreenName.RECOVERY_PHRASE_RESTORE -> {
                    Intent(currentActivity, com.flowfoundation.wallet.page.restore.keystore.KeyStoreRestoreActivity::class.java).apply {
                        putExtra("extra_restore_seed_phrase", true)
                    }
                }
                RNBridge.NativeScreenName.KEY_STORE_RESTORE -> {
                    Intent(currentActivity, com.flowfoundation.wallet.page.restore.keystore.KeyStoreRestoreActivity::class.java)
                }
                RNBridge.NativeScreenName.PRIVATE_KEY_RESTORE -> {
                    Intent(currentActivity, com.flowfoundation.wallet.page.restore.keystore.KeyStoreRestoreActivity::class.java).apply {
                        putExtra("extra_restore_private_key", true)
                    }
                }
                RNBridge.NativeScreenName.GOOGLE_DRIVE_RESTORE -> {
                    Intent(currentActivity, com.flowfoundation.wallet.page.walletrestore.WalletRestoreActivity::class.java)
                }
                RNBridge.NativeScreenName.MULTI_RESTORE -> {
                    Intent(currentActivity, com.flowfoundation.wallet.page.restore.multirestore.MultiRestoreActivity::class.java)
                }
                // Backup screens - not currently used for launching but defined in the enum
                RNBridge.NativeScreenName.MULTI_BACKUP,
                RNBridge.NativeScreenName.SEED_PHRASE_BACKUP,
                RNBridge.NativeScreenName.BACKUP_OPTIONS,
                RNBridge.NativeScreenName.WALLET_RESTORE -> {
                    logw(TAG, "launchNativeScreen() - screen $screenName not yet implemented")
                    return
                }
            }

            // Add flag to indicate launched from React Native
            intent.putExtra("from_react_native", true)
            currentActivity.startActivity(intent)
        } catch (e: Exception) {
            loge(TAG, "launchNativeScreen() error: ${e.message}")
            e.printStackTrace()
        }
    }

    companion object {
        const val NAME = "NativeFRWBridge"
    }
}
