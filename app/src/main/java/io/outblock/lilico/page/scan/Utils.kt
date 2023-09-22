package io.outblock.lilico.page.scan

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.webkit.URLUtil
import io.outblock.lilico.manager.config.AppConfig
import io.outblock.lilico.manager.walletconnect.WalletConnect
import io.outblock.lilico.page.browser.openBrowser
import io.outblock.lilico.utils.logd


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