package com.flowfoundation.wallet.page.scan

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.webkit.URLUtil
import com.flowfoundation.wallet.manager.config.AppConfig
import com.flowfoundation.wallet.manager.walletconnect.WalletConnect
import com.flowfoundation.wallet.page.browser.openBrowser
import com.flowfoundation.wallet.utils.logd


fun dispatchScanResult(context: Context, str: String) {
    val text = str.trim()
    if (text.isBlank()) {
        return
    }

    if ((text.startsWith("wc:") || text.startsWith("lilico://wc?") || text.startsWith("frw://wc?"))
        && AppConfig.walletConnectEnable()) {
        val wcUri = if (text.startsWith("wc:")) {
            text
        } else {
            Uri.parse(text).getQueryParameter("uri")
        } ?: return
        logd("wc", "wcUri: $wcUri")
        WalletConnect.get().pair(wcUri)
    } else if (URLUtil.isValidUrl(text.httpPrefix())) {
        openBrowser(context as Activity, text.httpPrefix())
    }
}

private fun String.httpPrefix(): String {
    if (startsWith("http")) return this

    return "https://$this"
}