package com.flowfoundation.wallet.widgets.webview

import android.webkit.WebView
import com.flowfoundation.wallet.widgets.webview.fcl.model.*
import com.google.gson.Gson

fun WebView?.executeJs(script: String) {
    this?.loadUrl("javascript:$script")
}

// FCL Extension methods
fun WebView.postPreAuthzResponse() {
    val script = "window.postMessage({type: 'FCL_RESPONSE', data: {type: 'PRE_AUTHZ', status: 'APPROVED'}}, '*');"
    executeJs(script)
}

fun WebView.postMessage(message: String) {
    val script = "window.postMessage($message, '*');"
    executeJs(script)
}

fun WebView.postAuthnViewReadyResponse(fcl: FclAuthnResponse, wallet: String) {
    val response = mapOf(
        "type" to "FCL_RESPONSE",
        "data" to mapOf(
            "type" to "AUTHN",
            "status" to "APPROVED",
            "data" to mapOf(
                "addr" to wallet,
                "services" to fcl.service
            )
        )
    )
    val script = "window.postMessage(${Gson().toJson(response)}, '*');"
    executeJs(script)
}

fun WebView.postSignMessageResponse(fcl: FclSignMessageResponse, signature: String) {
    val response = mapOf(
        "type" to "FCL_RESPONSE",
        "data" to mapOf(
            "type" to "USER_SIGNATURE",
            "status" to "APPROVED",
            "data" to mapOf(
                "addr" to signature,
                "signature" to signature
            )
        )
    )
    val script = "window.postMessage(${Gson().toJson(response)}, '*');"
    executeJs(script)
}

fun WebView.postAuthzPayloadSignResponse(fcl: FclAuthzResponse, signature: String) {
    val response = mapOf(
        "type" to "FCL_RESPONSE", 
        "data" to mapOf(
            "type" to "AUTHZ",
            "status" to "APPROVED",
            "data" to mapOf(
                "signature" to signature
            )
        )
    )
    val script = "window.postMessage(${Gson().toJson(response)}, '*');"
    executeJs(script)
}

fun WebView.postAuthzEnvelopeSignResponse(fcl: FclAuthzResponse, signature: String) {
    val response = mapOf(
        "type" to "FCL_RESPONSE",
        "data" to mapOf(
            "type" to "AUTHZ_ENVELOPE", 
            "status" to "APPROVED",
            "data" to mapOf(
                "signature" to signature
            )
        )
    )
    val script = "window.postMessage(${Gson().toJson(response)}, '*');"
    executeJs(script)
}

