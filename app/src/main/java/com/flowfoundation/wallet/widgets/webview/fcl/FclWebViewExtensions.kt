package com.flowfoundation.wallet.widgets.webview.fcl

import android.webkit.WebView
import org.onflow.flow.models.hexToBytes
import com.flowfoundation.wallet.manager.flowjvm.currentKeyId
import com.flowfoundation.wallet.manager.flowjvm.transaction.SignPayerResponse
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.wallet.walletAddress
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logv
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.webview.executeJs
import com.flowfoundation.wallet.widgets.webview.fcl.model.FclAuthnResponse
import com.flowfoundation.wallet.widgets.webview.fcl.model.FclAuthzResponse
import com.flowfoundation.wallet.widgets.webview.fcl.model.FclSignMessageResponse
import org.onflow.flow.models.FlowAddress
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.utils.logd

fun WebView?.postMessage(message: String) {
    uiScope {
        logv("WebView", "postMessage:$message")
        executeJs(
            """
        window && window.postMessage(JSON.parse(JSON.stringify($message || {})), '*')
    """.trimIndent()
        )
    }
}

fun WebView?.postAuthnViewReadyResponse(fcl: FclAuthnResponse, address: String) {
    ioScope {
        val response = fclAuthnResponse(fcl, address)
        postMessage(response)
    }
}

fun WebView?.postPreAuthzResponse() {
    ioScope {
        // Use a more reliable method to get the wallet address
        var address = WalletManager.selectedWalletAddress()
        
        // If that failed, try getting it from the AccountManager
        if (address.isBlank()) {
            val account = AccountManager.get()
            address = account?.wallet?.walletAddress() ?: ""
        }
        
        if (address.isBlank()) {
            logd("WebView", "No wallet address found for pre-authz response")
            return@ioScope
        }
        
        val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()
        val keyId = cryptoProvider?.let {
            FlowAddress(address).currentKeyId(it.getPublicKey())
        } ?: 0
        postMessage(fclPreAuthzResponse(address, keyId))
    }
}

fun WebView?.postAuthzPayloadSignResponse(fcl: FclAuthzResponse) {
    ioScope {
        // Use a more reliable method to get the wallet address
        var address = WalletManager.wallet()?.walletAddress()
        
        // If that failed, try getting it from the AccountManager
        if (address.isNullOrBlank()) {
            val account = AccountManager.get()
            address = account?.wallet?.walletAddress()
        }
        
        // If still blank, try getting from selectedWalletAddress
        if (address.isNullOrBlank()) {
            address = WalletManager.selectedWalletAddress()
        }
        
        if (address.isNullOrBlank()) {
            logd("WebView", "No wallet address found for authz payload sign response")
            return@ioScope
        }
        
        val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider() ?: return@ioScope
        val signature = cryptoProvider.signData(fcl.body.message.hexToBytes())
        val keyId = FlowAddress(address).currentKeyId(cryptoProvider.getPublicKey())
        fclAuthzResponse(address, signature, keyId).also { postMessage(it) }
    }
}

fun WebView?.postAuthzEnvelopeSignResponse(sign: SignPayerResponse.EnvelopeSigs) {
    ioScope {
        fclAuthzResponse(sign.address, sign.sig, sign.keyId).also { postMessage(it) }
    }
}

fun WebView?.postSignMessageResponse(fcl: FclSignMessageResponse) {
    ioScope {
        // Use a more reliable method to get the wallet address
        var address = WalletManager.wallet()?.walletAddress()
        
        // If that failed, try getting it from the AccountManager
        if (address.isNullOrBlank()) {
            val account = AccountManager.get()
            address = account?.wallet?.walletAddress()
        }
        
        // If still blank, try getting from selectedWalletAddress
        if (address.isNullOrBlank()) {
            address = WalletManager.selectedWalletAddress()
        }
        
        if (address.isNullOrBlank()) {
            logd("WebView", "No wallet address found for sign message response")
            return@ioScope
        }
        
        fclSignMessageResponse(fcl.body?.message, address).also { postMessage(it) }
    }
}