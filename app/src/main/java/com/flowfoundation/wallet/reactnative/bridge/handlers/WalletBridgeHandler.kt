package com.flowfoundation.wallet.reactnative.bridge.handlers

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.flow.wallet.errors.WalletError
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import org.onflow.flow.models.TransactionStatus
import org.onflow.flow.models.hexToBytes
import org.web3j.utils.Numeric

/**
 * Handler for wallet-related bridge methods
 * Handles: signing data, EVM signing, transaction monitoring
 */
class WalletBridgeHandler(private val reactContext: ReactApplicationContext) {

    private val TAG = "WalletBridgeHandler"

    fun sign(hexData: String, promise: Promise) {
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

    fun ethSign(hexData: String?, promise: Promise?) {
        ioScope {
            try {
                logd(TAG, "ethSign() called with hexData: $hexData")
                val eoaIndex = WalletManager.getSelectedEOAIndex()
                val signature = WalletManager.wallet()?.ethSignDigest(hexData?.hexToBytes() ?: throw IllegalArgumentException("hexData is null"), index = eoaIndex)
                if (signature != null && signature.isNotEmpty()) {
                    val result = Numeric.toHexString(signature)
                    logd(TAG, "ethSign() - signature $result")
                    uiScope {
                        promise?.resolve(result)
                    }
                } else {
                    uiScope {
                        promise?.reject("SIGN_ERROR", "Failed to sign data", null)
                    }
                }
            } catch (e: Exception) {
                uiScope {
                    promise?.reject("SIGN_ERROR", "Failed to sign data: ${e.message}", e)
                }
            }
        }
    }

    fun listenTransaction(txid: String) {
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

}
