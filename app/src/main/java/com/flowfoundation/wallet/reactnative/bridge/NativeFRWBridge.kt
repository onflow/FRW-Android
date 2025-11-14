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
import com.flowfoundation.wallet.manager.account.getFlowAddress
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.config.isGasFree
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.evm.EVMWalletManager.isValidEVMAddress
import com.flowfoundation.wallet.manager.evm.EVMWalletManager.toChecksumEVMAddress
import com.flowfoundation.wallet.manager.flowjvm.currentKeyId
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flow.wallet.CryptoProvider
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.network.model.WalletListData
import com.flowfoundation.wallet.manager.price.CurrencyManager
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.wallet.walletAddress
import com.flowfoundation.wallet.wallet.Wallet
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
import org.onflow.flow.models.toHexString
import org.web3j.utils.Numeric
import java.util.Locale
import com.flow.wallet.crypto.BIP39
import com.flowfoundation.wallet.reactnative.bridge.handlers.UtilsBridgeHandler
import com.flowfoundation.wallet.reactnative.bridge.handlers.AccountBridgeHandler
import com.flowfoundation.wallet.reactnative.bridge.handlers.AuthBridgeHandler
import com.flowfoundation.wallet.reactnative.bridge.handlers.UIBridgeHandler
import com.flowfoundation.wallet.reactnative.bridge.handlers.WalletBridgeHandler

class NativeFRWBridge(reactContext: ReactApplicationContext) : NativeFRWBridgeSpec(reactContext) {

    private val TAG = "NativeFRWBridge"

    // Delegate handlers for different bridge functionality domains
    private val utilsHandler = UtilsBridgeHandler(reactContext)
    private val accountHandler = AccountBridgeHandler(reactContext)
    private val authHandler = AuthBridgeHandler(reactContext)
    private val uiHandler = UIBridgeHandler(reactContext)
    private val walletHandler = WalletBridgeHandler(reactContext)

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

    override fun registerSecureTypeAccount(username: String, promise: Promise) = authHandler.registerSecureTypeAccount(username, promise)

    override fun createLinkedCOAAccount(promise: Promise) = authHandler.createLinkedCOAAccount(promise)

    override fun generateSeedPhrase(strength: Double?, promise: Promise) = authHandler.generateSeedPhrase(strength, promise, ::bridgeModelToWritableMap)

    override fun signOutAndSignInAnonymously(promise: Promise) = authHandler.signOutAndSignInAnonymously(promise)

    override fun signInWithCustomToken(customToken: String, promise: Promise) = authHandler.signInWithCustomToken(customToken, promise)

    override fun saveMnemonic(mnemonic: String, customToken: String, txId: String, username: String, promise: Promise) = authHandler.saveMnemonic(mnemonic, customToken, txId, username, promise)

    override fun requestNotificationPermission(promise: Promise) = utilsHandler.requestNotificationPermission(promise)

    override fun checkNotificationPermission(promise: Promise) = utilsHandler.checkNotificationPermission(promise)

    override fun logToNative(level: String, message: String, args: ReadableArray) = utilsHandler.logToNative(level, message, args)

    override fun setScreenSecurityLevel(level: String) = utilsHandler.setScreenSecurityLevel(level)

    override fun launchNativeScreen(screenName: String, params: String?) = uiHandler.launchNativeScreen(screenName, params)

    companion object {
        const val NAME = "NativeFRWBridge"
    }
}
