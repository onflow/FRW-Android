package com.flowfoundation.wallet.bridge

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableNativeMap
import com.facebook.react.bridge.WritableNativeArray
import com.flow.wallet.errors.WalletError
import com.flowfoundation.wallet.bridge.NativeFRWBridgeSpec
import com.flowfoundation.wallet.firebase.auth.getFirebaseJwt
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.BuildConfig
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.emoji.model.Emoji
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.cache.recentTransactionCache
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryCOATokenBalance
import com.flowfoundation.wallet.manager.flowjvm.currentKeyId
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.manager.config.isGasFree
import java.math.BigDecimal
import org.onflow.flow.models.hexToBytes
import org.onflow.flow.models.FlowAddress
import android.content.Intent
import com.flowfoundation.wallet.page.scan.ScanBarcodeActivity

class NativeFRWBridge(reactContext: ReactApplicationContext) : NativeFRWBridgeSpec(reactContext) {

    override fun getName() = NAME

    override fun getSelectedAddress(): String? {
        return WalletManager.selectedWalletAddress()
    }

    override fun getNetwork(): String {
        return chainNetWorkString()
    }

    override fun getJWT(promise: Promise) {
        ioScope {
            try {
                val jwt = getFirebaseJwt()
                uiScope {
                    promise.resolve(jwt)
                }
            } catch (e: Exception) {
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

                if (!recentData.isNullOrEmpty()) {
                    val contactsArray = WritableNativeArray()
                    recentData.forEach { contact ->
                        val contactMap = WritableNativeMap().apply {
                            putString("id", contact.id)
                            putString("name", contact.name())
                            putString("address", contact.address)
                            putString("avatar", contact.avatar)
                            putString("username", contact.username)
                            putString("contactName", contact.contactName)
                        }
                        contactsArray.pushMap(contactMap)
                    }

                    val result = WritableNativeMap().apply {
                        putArray("contacts", contactsArray)
                    }

                    uiScope {
                        promise.resolve(result)
                    }
                } else {
                    val result = WritableNativeMap().apply {
                        putArray("contacts", WritableNativeArray())
                    }
                    uiScope {
                        promise.resolve(result)
                    }
                }
            } catch (e: Exception) {
                val result = WritableNativeMap().apply {
                    putArray("contacts", WritableNativeArray())
                }
                uiScope {
                    promise.resolve(result)
                }
            }
        }
    }

    override fun getWalletAccounts(promise: Promise) {
        ioScope {
            try {
                val accountsArray = WritableNativeArray()

                // Get main wallet address
                val mainAddress = WalletManager.selectedWalletAddress()
                if (mainAddress.isNotEmpty()) {
                    // Use AccountEmojiManager to get proper name and emoji
                    val emojiInfo = AccountEmojiManager.getEmojiByAddress(mainAddress)
                    val emoji = Emoji.getEmojiById(emojiInfo.emojiId)

                    val accountMap = WritableNativeMap().apply {
                        putString("id", "main")
                        putString("name", emojiInfo.emojiName)
                        putString("address", mainAddress)
                        putString("emoji", emoji)
                        putBoolean("isActive", true)
                        putBoolean("isIncompatible", false)
                        putString("type", "main")
                    }
                    accountsArray.pushMap(accountMap)
                }

                // Get child accounts
                try {
                    val childAccounts = WalletManager.childAccountList(mainAddress)?.get()
                    childAccounts?.forEach { childAccount ->
                        // Debug: Log child account data to see if icon is available
                        println("DEBUG: Child account - name: ${childAccount.name}, icon: ${childAccount.icon}, address: ${childAccount.address}")

                        val accountMap = WritableNativeMap().apply {
                            putString("id", "child_${childAccount.address}")
                            putString("name", childAccount.name ?: "Child Account")
                            putString("address", childAccount.address)
                            putString("emoji", "👶") // Default emoji for child accounts
                            // Add the child account's icon as avatar if available (this should show the squid!)
                            childAccount.icon?.let {
                                println("DEBUG: Setting avatar for child account: $it")
                                putString("avatar", it)
                            }
                            putBoolean("isActive", false)
                            putBoolean("isIncompatible", false)
                            putString("type", "child")
                        }
                        accountsArray.pushMap(accountMap)
                    }
                } catch (e: Exception) {
                    // Child accounts might not be available, continue without them
                    println("Child accounts not available: ${e.message}")
                }

                // Get EVM address if available
                try {
                    val evmAddress = EVMWalletManager.getEVMAddress()
                    if (!evmAddress.isNullOrEmpty()) {
                        // Use AccountEmojiManager for EVM account as well
                        val emojiInfo = AccountEmojiManager.getEmojiByAddress(evmAddress)
                        val emoji = Emoji.getEmojiById(emojiInfo.emojiId)

                        val accountMap = WritableNativeMap().apply {
                            putString("id", "evm")
                            putString("name", emojiInfo.emojiName)
                            putString("address", evmAddress)
                            putString("emoji", emoji)
                            putBoolean("isActive", false)
                            putBoolean("isIncompatible", false)
                            putString("type", "evm")
                        }
                        accountsArray.pushMap(accountMap)
                    }
                } catch (e: Exception) {
                    // EVM account might not be available, continue without it
                    println("EVM account not available: ${e.message}")
                }

                val result = WritableNativeMap().apply {
                    putArray("accounts", accountsArray)
                }

                uiScope {
                    promise.resolve(result)
                }
            } catch (e: Exception) {
                val result = WritableNativeMap().apply {
                    putArray("accounts", WritableNativeArray())
                }
                uiScope {
                    promise.resolve(result)
                }
            }
        }
    }

    override fun closeRN() {
        try {
            val currentActivity = reactApplicationContext.currentActivity
            currentActivity?.finish()
        } catch (e: Exception) {
            // If finishing activity fails, log error but don't crash
            println("Failed to close React Native activity: ${e.message}")
        }
    }

    override fun getSignKeyIndex(): Double {
        return try {
            val address = WalletManager.selectedWalletAddress()
            val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()

            if (address.isEmpty() || cryptoProvider == null) {
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

    override fun getAllEnvVars(promise: Promise) {
        try {
            val envVars = WritableNativeMap().apply {
                // Standard environment variables
                putString("API_BASE_URL", BuildConfig.API_BASE_URL ?: "")
                putString("API_KEY", BuildConfig.API_KEY ?: "")
                putBoolean("DEBUG_MODE", BuildConfig.DEBUG)
                putString("APP_VERSION", BuildConfig.VERSION_NAME)
                putBoolean("ANALYTICS_ENABLED", BuildConfig.ANALYTICS_ENABLED ?: false)
                putString("ENVIRONMENT", if (BuildConfig.DEBUG) "development" else "production")
                putString("FLOW_NETWORK", chainNetWorkString())
                putString("FLOW_ACCESS_NODE_URL", BuildConfig.FLOW_ACCESS_NODE_URL ?: "")
                putString("FLOW_DISCOVERY_WALLET_URL", BuildConfig.FLOW_DISCOVERY_WALLET_URL ?: "")
                
                // Native App Environment Variables
                putString("DRIVE_AES_IV", BuildConfig.DRIVE_AES_IV ?: "")
                putString("DRIVE_AES_KEY", BuildConfig.DRIVE_AES_KEY ?: "")
                putString("WALLET_CONNECT_PROJECT_ID", BuildConfig.WALLET_CONNECT_PROJECT_ID ?: "")
                putString("INSTABUG_TOKEN_DEV", BuildConfig.INSTABUG_TOKEN_DEV ?: "")
                putString("INSTABUG_TOKEN_PROD", BuildConfig.INSTABUG_TOKEN_PROD ?: "")
                putString("CROWDIN_PROJECT_ID", BuildConfig.CROWDIN_PROJECT_ID ?: "")
                putString("CROWDIN_API_TOKEN", BuildConfig.CROWDIN_API_TOKEN ?: "")
                putString("CROWDIN_DISTRIBUTION", BuildConfig.CROWDIN_DISTRIBUTION ?: "")
                putString("MIXPANEL_TOKEN_DEV", BuildConfig.MIXPANEL_TOKEN_DEV ?: "")
                putString("MIXPANEL_TOKEN_PROD", BuildConfig.MIXPANEL_TOKEN_PROD ?: "")
                putString("DROPBOX_APP_KEY_DEV", BuildConfig.DROPBOX_APP_KEY ?: "")
                putString("DROPBOX_APP_KEY_PROD", BuildConfig.DROPBOX_APP_KEY ?: "")
                putString("X_SIGNATURE_KEY", BuildConfig.X_SIGNATURE_KEY ?: "")
            }
            
            promise.resolve(envVars)
        } catch (e: Exception) {
            promise.reject("ENV_ERROR", "Failed to get environment variables: ${e.message}", e)
        }
    }

    companion object {
        const val NAME = "NativeFRWBridge"
    }
}
