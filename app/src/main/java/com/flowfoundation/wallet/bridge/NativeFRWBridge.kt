package com.flowfoundation.wallet.bridge

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.flow.wallet.errors.WalletError
import com.flowfoundation.wallet.bridge.NativeFRWBridgeSpec
import com.flowfoundation.wallet.firebase.auth.getFirebaseJwt
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.utils.ioScope
import kotlinx.coroutines.launch
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
        ioScope.launch {
            try {
                val jwt = getFirebaseJwt()
                promise.resolve(jwt)
            } catch (e: Exception) {
                promise.reject("JWT_ERROR", "Failed to get Firebase JWT: ${e.message}", e)
            }
        }
    }

    override fun sign(hexData: String, promise: Promise) {
        ioScope.launch {
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

    companion object {
        const val NAME = "NativeFRWBridge"
    }
}
