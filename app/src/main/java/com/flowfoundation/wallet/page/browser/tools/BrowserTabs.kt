package com.flowfoundation.wallet.page.browser.tools

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.view.children
import com.flowfoundation.wallet.page.browser.browserViewBinding
import com.flowfoundation.wallet.page.browser.releaseBrowser
import com.flowfoundation.wallet.page.browser.saveRecentRecord
import com.flowfoundation.wallet.page.browser.widgets.LilicoWebView
import com.flowfoundation.wallet.page.window.WindowFrame
import com.flowfoundation.wallet.utils.extensions.removeFromParent
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.loge
import java.util.*

private val tabs = mutableListOf<BrowserTab>()

fun popBrowserTab(tabId: String) {
    val webViewContainer = webViewContainer() ?: return

    val tab = tabs.firstOrNull { it.id == tabId } ?: return
    tabs.removeAll { it.id == tabId }

    tab.webView.saveRecentRecord()

    webViewContainer.removeWebView(tab.webView)
    tab.webView.destroyWebView()

    if (tabs.isEmpty()) {
        releaseBrowser()
    }
}

fun pushBrowserTab(tab: BrowserTab) {
    val webViewContainer = webViewContainer() ?: return

    tabs.remove(tab)
    tabs.add(tab)

    cleanCallbacks()

    webViewContainer.cleanWebView()
    tab.webView.removeFromParent()
    webViewContainer.addView(tab.webView)
}

fun showBrowserLastTab() {
    browserTabLast()?.let { changeBrowserTab(it.id) }
}

fun changeBrowserTab(tabId: String) {
    val tab = tabs.firstOrNull { it.id == tabId } ?: return
    pushBrowserTab(tab)
}

fun browserTabLast(): BrowserTab? = tabs.lastOrNull()

fun newBrowserTab(context: Context, url: String? = null): BrowserTab {
    return BrowserTab(
        id = UUID.randomUUID().toString(),
        webView = LilicoWebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            url?.let { loadUrl(it) }
        }
    )
}

fun newAndPushBrowserTab(url: String? = null): BrowserTab? {
    val webViewContainer = webViewContainer() ?: return null
    val tab = newBrowserTab(webViewContainer.context, url)
    pushBrowserTab(tab)
    return tab
}

fun clearBrowserTabs() {
    // Destroy each WebView before dropping the references. Without this the
    // chromium-side resources (renderer process, GPU buffers, JS heap) stay
    // alive until the GC happens to collect the wrapper, which is a major
    // contributor to the OOM reports filed against ExploreFragment.
    tabs.forEach { runCatching { it.webView.destroyWebView() }.onFailure { loge(it) } }
    tabs.clear()
}

fun removeTabAndHideBrowser(tabId: String) {
    popBrowserTab(tabId)
    WindowFrame.browserContainer()?.setVisible(false)
}

fun browserTabsCount() = tabs.size

fun removeWebview() {
    webViewContainer()?.cleanWebView()
}

private fun webViewContainer() = browserViewBinding()?.webviewContainer

private fun ViewGroup.cleanWebView() {
    children.forEach { if (it is WebView) removeView(it) }
}

private fun ViewGroup.removeWebView(webView: WebView) {
    children.forEach { if (it == webView) removeView(it) }
}

private fun cleanCallbacks() {
    tabs.forEach { it.webView.setWebViewCallback(null) }
}

/**
 * Standard cleanup sequence recommended by the Android WebView team. Skipping
 * any of these steps tends to leave the renderer process or the chromium JS
 * heap allocated, which shows up later as an OOM in an unrelated allocation.
 */
private fun LilicoWebView.destroyWebView() {
    runCatching {
        stopLoading()
        setWebViewCallback(null)
        loadUrl("about:blank")
        clearHistory()
        removeFromParent()
        removeAllViews()
        destroy()
    }.onFailure { loge(it) }
}

class BrowserTab(
    val id: String,
    val webView: LilicoWebView,
) {
    fun icon() = webView.favicon

    fun title() = webView.title

    fun url() = webView.url
}