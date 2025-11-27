package com.flowfoundation.wallet.reactnative.bridge.handlers

import com.facebook.react.bridge.Promise
import com.flowfoundation.wallet.reactnative.bridge.NativeFRWBridge
import com.flowfoundation.wallet.reactnative.bridge.RNBridge
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.WritableMap
import com.flowfoundation.wallet.BuildConfig
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.config.isGasFree
import com.flowfoundation.wallet.manager.price.CurrencyManager
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.network.API_HOST
import com.flowfoundation.wallet.network.BASE_HOST
import com.flowfoundation.wallet.page.profile.subpage.currency.model.selectedCurrency
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.isDev
import com.flowfoundation.wallet.utils.isTesting
import com.flowfoundation.wallet.utils.logToInstabug
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logw
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import java.util.Locale

/**
 * Handler for utility/helper bridge methods
 * Handles: app info, network, environment, currency, logging, permissions, screen security
 */
class UtilsBridgeHandler(private val reactContext: ReactApplicationContext) {

    private val TAG = "UtilsBridgeHandler"

    fun getName(): String {
        logd(TAG, "getName() called, returning: ${NativeFRWBridge.NAME}")
        return NativeFRWBridge.NAME
    }

    fun getNetwork(): String {
        return try {
            val network = chainNetWorkString()
            logd(TAG, "getNetwork() called, returning: $network")
            network
        } catch (e: Exception) {
            loge(TAG, "getNetwork() error: ${e.message}")
            "mainnet"
        }
    }

    fun getVersion(): String {
        return BuildConfig.VERSION_NAME
    }

    fun getBuildNumber(): String {
        return BuildConfig.VERSION_CODE.toString()
    }

    fun getLanguage(): String? {
        return Locale.getDefault().language
    }

    fun getDeviceId(): String {
        return DeviceInfoManager.getDeviceID()
    }

    fun isFreeGasEnabled(promise: Promise) {
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

    fun getEnv(bridgeModelToWritableMap: (Any) -> WritableMap): WritableMap {
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

    fun getCurrency(bridgeModelToWritableMap: (Any) -> WritableMap): WritableMap {
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

    fun getTokenRate(token: String): String {
        return try {
            val fungibleToken = FungibleTokenListManager.getTokenById(token)
            fungibleToken?.tokenPrice()?.toString() ?: "0.0"
        } catch (e: Exception) {
            // Return "0.0" on error
            "0.0"
        }
    }

    fun requestNotificationPermission(promise: Promise) {
        logd(TAG, "requestNotificationPermission() called")
        try {
            val currentActivity = reactContext.currentActivity

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

    fun checkNotificationPermission(promise: Promise) {
        logd(TAG, "checkNotificationPermission() called")
        try {
            val isGranted = com.flowfoundation.wallet.utils.isNotificationPermissionGrand(reactContext)
            logd(TAG, "checkNotificationPermission() - isGranted: $isGranted")

            uiScope {
                promise.resolve(isGranted)
            }
        } catch (e: Exception) {
            loge(TAG, "checkNotificationPermission() - error: ${e.message}")
            e.printStackTrace()
            uiScope {
                promise.reject("PERMISSION_ERROR", "Failed to check notification permission: ${e.message}", e)
            }
        }
    }

    fun logToNative(level: String, message: String, args: ReadableArray) {
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

    fun setScreenSecurityLevel(level: String) {
        logd(TAG, "setScreenSecurityLevel() called with level: $level")
        try {
            val currentActivity = reactContext.currentActivity

            if (currentActivity == null) {
                logw(TAG, "setScreenSecurityLevel() - no current activity")
                return
            }

            currentActivity.runOnUiThread {
                when (level) {
                    "secure" -> {
                        // Prevent screenshots and screen recording
                        logd(TAG, "setScreenSecurityLevel() - enabling secure mode")
                        currentActivity.window.setFlags(
                            android.view.WindowManager.LayoutParams.FLAG_SECURE,
                            android.view.WindowManager.LayoutParams.FLAG_SECURE
                        )
                    }
                    "normal" -> {
                        // Allow screenshots and screen recording
                        logd(TAG, "setScreenSecurityLevel() - disabling secure mode")
                        currentActivity.window.clearFlags(
                            android.view.WindowManager.LayoutParams.FLAG_SECURE
                        )
                    }
                    else -> {
                        logw(TAG, "setScreenSecurityLevel() - unknown level: $level")
                    }
                }
            }
        } catch (e: Exception) {
            loge(TAG, "setScreenSecurityLevel() - error: ${e.message}")
            e.printStackTrace()
        }
    }
}
