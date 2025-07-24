package com.flowfoundation.wallet.bridge

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
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
import com.flowfoundation.wallet.cache.addressBookCache
import com.flowfoundation.wallet.cache.recentTransactionCache
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryCOATokenBalance
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.uiScope
import com.google.gson.Gson
import java.math.BigDecimal
import org.onflow.flow.models.hexToBytes

class NativeFRWBridge(reactContext: ReactApplicationContext) : NativeFRWBridgeSpec(reactContext) {

    override fun getName() = NAME

    override fun getSelectedAddress(): String? {
        return WalletManager.selectedWalletAddress()
    }

    override fun getNetwork(): String? {
        return chainNetWorkString()
    }

    override fun getJWT(promise: Promise) {
        ioScope {
            try {
                val jwt = getFirebaseJwt()
                promise.resolve(jwt)
            } catch (e: Exception) {
                promise.reject("JWT_ERROR", "Failed to get Firebase JWT: ${e.message}", e)
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
                    promise.resolve(signature)
                } else {
                    promise.reject("SIGN_ERROR", "Failed to sign data", null)
                }
            } catch (e: Exception) {
                promise.reject("SIGN_ERROR", "Failed to sign data: ${e.message}", e)
            }
        }
    }

    override fun getAddressBook(promise: Promise) {
        ioScope {
            try {
                // First try to get from cache
                val cachedData = addressBookCache().read()?.contacts
                if (!cachedData.isNullOrEmpty()) {
                    val gson = Gson()
                    val contactsJson = gson.toJson(cachedData.map { contact ->
                        mapOf(
                            "id" to contact.id,
                            "name" to contact.name(),
                            "address" to contact.address,
                            "avatar" to contact.avatar,
                            "username" to contact.username,
                            "contactName" to contact.contactName
                        )
                    })

                    uiScope {
                        promise.resolve(contactsJson)
                    }
                    return@ioScope
                }

                // If no cache, fetch from API
                val service = retrofit().create(ApiService::class.java)
                val response = service.getAddressBook()

                if (response.status == 200 && !response.data.contacts.isNullOrEmpty()) {
                    // Cache the response
                    addressBookCache().cache(response.data)

                    val gson = Gson()
                    val contactsJson = gson.toJson(response.data.contacts?.map { contact ->
                        mapOf(
                            "id" to contact.id,
                            "name" to contact.name(),
                            "address" to contact.address,
                            "avatar" to contact.avatar,
                            "username" to contact.username,
                            "contactName" to contact.contactName
                        )
                    })

                    uiScope {
                        promise.resolve(contactsJson)
                    }
                } else {
                    uiScope {
                        promise.resolve("[]") // Return empty array if no contacts
                    }
                }
            } catch (e: Exception) {
                uiScope {
                    promise.resolve("[]") // Return empty array on error instead of rejecting
                }
            }
        }
    }

    override fun getRecentContacts(promise: Promise) {
        ioScope {
            try {
                val recentData = recentTransactionCache().read()?.contacts

                if (!recentData.isNullOrEmpty()) {
                    val gson = Gson()
                    val contactsJson = gson.toJson(recentData.map { contact ->
                        mapOf(
                            "id" to contact.id,
                            "name" to contact.name(),
                            "address" to contact.address,
                            "avatar" to contact.avatar,
                            "username" to contact.username,
                            "contactName" to contact.contactName
                        )
                    })

                    uiScope {
                        promise.resolve(contactsJson)
                    }
                } else {
                    uiScope {
                        promise.resolve("[]") // Return empty array if no recent contacts
                    }
                }
            } catch (e: Exception) {
                uiScope {
                    promise.resolve("[]") // Return empty array on error instead of rejecting
                }
            }
        }
    }

    override fun getWalletAccounts(promise: Promise) {
        ioScope {
            try {
                val accounts = mutableListOf<Map<String, Any?>>()

                // Get main wallet address
                val mainAddress = WalletManager.selectedWalletAddress()
                if (mainAddress.isNotEmpty()) {
                    // Use AccountEmojiManager to get proper name and emoji
                    val emojiInfo = AccountEmojiManager.getEmojiByAddress(mainAddress)
                    val emoji = Emoji.getEmojiById(emojiInfo.emojiId)

                    accounts.add(mapOf(
                        "id" to "main",
                        "name" to emojiInfo.emojiName,
                        "address" to mainAddress,
                        "emoji" to emoji,
                        "isActive" to true,
                        "isIncompatible" to false,
                        "type" to "main"
                    ))
                }

                // Get child accounts
                try {
                    val childAccounts = WalletManager.childAccountList(mainAddress)?.get()
                    childAccounts?.forEach { childAccount ->
                        accounts.add(mapOf(
                            "id" to "child_${childAccount.address}",
                            "name" to (childAccount.name ?: "Child Account"),
                            "address" to childAccount.address,
                            "emoji" to "👶", // Default emoji for child accounts
                            "isActive" to false,
                            "isIncompatible" to false,
                            "type" to "child"
                        ))
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

                        accounts.add(mapOf(
                            "id" to "evm",
                            "name" to emojiInfo.emojiName,
                            "address" to evmAddress,
                            "emoji" to emoji,
                            "isActive" to false,
                            "isIncompatible" to false,
                            "type" to "evm"
                        ))
                    }
                } catch (e: Exception) {
                    // EVM account might not be available, continue without it
                    println("EVM account not available: ${e.message}")
                }

                val gson = Gson()
                val accountsJson = gson.toJson(accounts)

                uiScope {
                    promise.resolve(accountsJson)
                }
            } catch (e: Exception) {
                uiScope {
                    promise.resolve("[]") // Return empty array on error
                }
            }
        }
    }

    override fun getCOAFlowBalance(promise: Promise) {
        ioScope {
            try {
                val balance = cadenceQueryCOATokenBalance() ?: BigDecimal.ZERO

                uiScope {
                    promise.resolve(balance.toString())
                }
            } catch (e: Exception) {
                uiScope {
                    promise.resolve("0") // Return 0 on error instead of rejecting
                }
            }
        }
    }

    companion object {
        const val NAME = "NativeFRWBridge"
    }
}
