package io.outblock.lilico.widgets.webview.fcl

import android.webkit.WebView
import com.nftco.flow.sdk.FlowAddress
import com.nftco.flow.sdk.hexToBytes
import io.outblock.lilico.manager.flowjvm.currentKeyId
import io.outblock.lilico.manager.flowjvm.transaction.SignPayerResponse
import io.outblock.lilico.manager.key.CryptoProviderManager
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.logv
import io.outblock.lilico.utils.uiScope
import io.outblock.lilico.widgets.webview.executeJs
import io.outblock.lilico.widgets.webview.fcl.model.FclAuthnResponse
import io.outblock.lilico.widgets.webview.fcl.model.FclAuthzResponse
import io.outblock.lilico.widgets.webview.fcl.model.FclSignMessageResponse

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
        val address = WalletManager.selectedWalletAddress()
        val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()
        val keyId = cryptoProvider?.let {
            FlowAddress(address).currentKeyId(it.getPublicKey())
        } ?: 0
        postMessage(fclPreAuthzResponse(address, keyId))
    }
}

fun WebView?.postAuthzPayloadSignResponse(fcl: FclAuthzResponse) {
    ioScope {
        val address = WalletManager.selectedWalletAddress()
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
        val address = WalletManager.selectedWalletAddress()
        fclSignMessageResponse(fcl.body?.message, address).also { postMessage(it) }
    }
}