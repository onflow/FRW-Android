package com.flowfoundation.wallet.widgets.webview.fcl

import android.webkit.WebView
import com.nftco.flow.sdk.DomainTag
import com.flowfoundation.wallet.widgets.webview.fcl.model.FclAuthnResponse
import com.flowfoundation.wallet.widgets.webview.fcl.model.FclAuthzResponse
import com.flowfoundation.wallet.widgets.webview.fcl.model.FclDialogModel
import org.tdf.rlp.RLPCodec

private val accountProofTag = DomainTag.normalize("FCL-ACCOUNT-PROOF-V0.0")

// encode flow jvm account proof
fun FclAuthnResponse.encodeAccountProof(address: String, includeDomainTag: Boolean = true): ByteArray {
    val nonce = body.nonce ?: return byteArrayOf()
    val appIdentifier = body.appIdentifier ?: throw IllegalStateException("Encode Message For Provable Authn Error: appIdentifier must be defined")
    return encodeAccountProof(address, nonce, appIdentifier, includeDomainTag)
}

fun encodeAccountProof(
    address: String,
    nonce: String,
    appIdentifier: String,
    includeDomainTag: Boolean,
): ByteArray {
    assert(address.isNotBlank()) { "Encode Message For Provable Authn Error: address must be defined" }
    assert(nonce.length >= 64) { "Encode Message For Provable Authn Error: nonce must be minimum of 32 bytes" }

    val rpl = RLPCodec.encode(AccountProof())

    return if (includeDomainTag) {
        accountProofTag + rpl
    } else rpl
}

private class AccountProof

fun FclAuthzResponse.toFclDialogModel(webView: WebView): FclDialogModel {
    return FclDialogModel(
        signMessage = body.message,
        cadence = body.cadence,
        url = webView.url,
        title = webView.title,
        logo = config?.app?.icon,
        network = config?.client?.network,
    )
}