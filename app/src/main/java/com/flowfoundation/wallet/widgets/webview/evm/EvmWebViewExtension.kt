package com.flowfoundation.wallet.widgets.webview.evm

import android.webkit.WebView

fun WebView.setAddress(network: String, address: String, methodId: Long) {
    val setAddress = "window.$network.setAddress(\"$address\");"
    val callback = "window.$network.sendResponse($methodId, [\"$address\"])"
    this.post {
        this.evaluateJavascript(setAddress) {}
        this.evaluateJavascript(callback) {}
    }
}

fun WebView.sendError(network: String, message: String, methodId: Long) {
    val script = "window.$network.sendError($methodId, \"$message\")"
    this.post {
        this.evaluateJavascript(script) {}
    }
}

fun WebView.sendResult(network: String, message: String, methodId: Long) {
    val script = "window.$network.sendResponse($methodId, \"$message\")"
    this.post {
        this.evaluateJavascript(script) {}
    }
}

fun WebView.sendNull(network: String, methodId: Long) {
    val script = "window.$network.sendResponse($methodId, null)"
    this.post {
        this.evaluateJavascript(script) {}
    }
}
