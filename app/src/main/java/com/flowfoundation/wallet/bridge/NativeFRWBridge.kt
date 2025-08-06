package com.flowfoundation.wallet.bridge

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableNativeMap
import com.facebook.react.bridge.WritableNativeArray
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.flow.wallet.errors.WalletError
import com.flowfoundation.wallet.bridge.NativeFRWBridgeSpec
import com.flowfoundation.wallet.firebase.auth.getFirebaseJwt
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.wallet.walletAddress
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
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import org.onflow.flow.models.TransactionStatus
import java.math.BigDecimal
import org.onflow.flow.models.hexToBytes
import org.onflow.flow.models.FlowAddress
import android.content.Intent
import com.flowfoundation.wallet.page.scan.ScanBarcodeActivity
import com.google.gson.Gson
import org.json.JSONObject
import org.json.JSONArray
import android.os.Bundle

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

                // Get main wallet address
                val mainAddress = WalletManager.wallet()?.walletAddress()
                val mainEmojiInfo = createEmojiInfo(mainAddress)
                if (!mainAddress.isNullOrEmpty()) {
                    val mainAccount = RNBridge.WalletAccount(
                        id = "main",
                        name = mainEmojiInfo?.name ?: "Main Account",
                        address = mainAddress,
                        emojiInfo = mainEmojiInfo,
                        parentEmoji = null,
                        avatar = null,
                        isActive = isSelectedWalletAddress(mainAddress),
                        type = RNBridge.AccountType.MAIN
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
                            avatar = childAccount.icon, // Include the squid avatar!
                            isActive = isSelectedWalletAddress(childAccount.address),
                            type = RNBridge.AccountType.CHILD
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
                            emojiInfo = evmEmojiInfo,
                            parentEmoji = mainEmojiInfo,
                            avatar = null,
                            isActive = isSelectedWalletAddress(evmAddress),
                            type = RNBridge.AccountType.EVM
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
            val address = WalletManager.wallet()?.walletAddress()
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

    override fun getProposer(promise: Promise) {
        ioScope {
            try {
                // TODO: Implement getProposer logic
                val proposer = WalletManager.selectedWalletAddress()
                uiScope {
                    promise.resolve(proposer)
                }
            } catch (e: Exception) {
                uiScope {
                    promise.reject("PROPOSER_ERROR", "Failed to get proposer: ${e.message}", e)
                }
            }
        }
    }

    override fun getPayer(promise: Promise) {
        ioScope {
            try {
                // TODO: Implement getPayer logic
                val payer = WalletManager.selectedWalletAddress()
                uiScope {
                    promise.resolve(payer)
                }
            } catch (e: Exception) {
                uiScope {
                    promise.reject("PAYER_ERROR", "Failed to get payer: ${e.message}", e)
                }
            }
        }
    }

    override fun getAuthorizations(promise: Promise) {
        ioScope {
            try {
                // TODO: Implement getAuthorizations logic
                val authorizations = WritableNativeArray()
                uiScope {
                    promise.resolve(authorizations)
                }
            } catch (e: Exception) {
                uiScope {
                    promise.reject("AUTHORIZATIONS_ERROR", "Failed to get authorizations: ${e.message}", e)
                }
            }
        }
    }

    override fun getEnvKeys(): WritableMap {
        val envMap = WritableNativeMap()
        
        // Add environment keys - these would typically come from BuildConfig or environment
        envMap.putString("NODE_API_URL", BuildConfig.NODE_API_URL ?: "")
        envMap.putString("GO_API_URL", BuildConfig.GO_API_URL ?: "")
        envMap.putString("INSTABUG_TOKEN", BuildConfig.INSTABUG_TOKEN ?: "")
        
        return envMap
    }


    private val gson = Gson()

    // Helper method to convert Bridge models to React Native data
    private fun bridgeModelToWritableMap(model: Any): WritableNativeMap {
        val json = gson.toJson(model)
        return jsonToWritableMap(json)
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
                    is Double -> map.putDouble(key, value)
                    is JSONArray -> map.putArray(key, jsonArrayToWritableArray(value))
                    is JSONObject -> map.putMap(key, jsonToWritableMap(value.toString()))
                    JSONObject.NULL -> map.putNull(key)
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
                    is Double -> array.pushDouble(value)
                    is JSONObject -> array.pushMap(jsonToWritableMap(value.toString()))
                    is JSONArray -> array.pushArray(jsonArrayToWritableArray(value))
                    JSONObject.NULL -> array.pushNull()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return array
    }

    // Helper method to create EmojiInfo from AccountEmojiManager data
    private fun createEmojiInfo(address: String?): RNBridge.EmojiInfo? {
        if (address.isNullOrEmpty()) {
            return null
        }

        val emojiInfo = AccountEmojiManager.getEmojiByAddress(address)
        val emoji = Emoji.getEmojiById(emojiInfo.emojiId)
        val colorHex = Emoji.getEmojiColorHex(emojiInfo.emojiId)

        return RNBridge.EmojiInfo(
            emoji = emoji,
            name = emojiInfo.emojiName,
            color = colorHex
        )
    }

    // Helper method to check if address is the selected wallet address (case-insensitive)
    private fun isSelectedWalletAddress(address: String?): Boolean {
        if (address.isNullOrEmpty()) {
            return false
        }

        val selectedAddress = WalletManager.selectedWalletAddress()
        return selectedAddress.equals(address, ignoreCase = true)
    }

    companion object {
        const val NAME = "NativeFRWBridge"
    }
}
