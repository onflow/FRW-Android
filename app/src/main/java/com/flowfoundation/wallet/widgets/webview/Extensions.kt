package com.flowfoundation.wallet.widgets.webview

import android.webkit.WebView
import com.just.agentweb.AgentWeb


fun WebView?.executeJs(script: String) {
    this?.loadUrl("javascript:$script")
}

fun AgentWeb?.callJs(functionName: String, params: List<String>? = null) {
    if (params.isNullOrEmpty()) {
        this?.jsAccessEntrace?.quickCallJs(functionName)
    } else {
        this?.jsAccessEntrace?.quickCallJs(functionName, *params.toTypedArray())
    }
}