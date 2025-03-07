package com.flowfoundation.wallet.widgets.webview

import android.webkit.WebView


fun WebView?.executeJs(script: String) {
    this?.loadUrl("javascript:$script")
}

