package com.flowfoundation.wallet.page.browser

import android.app.Activity
import androidx.core.view.children
import com.flowfoundation.wallet.page.browser.tools.clearBrowserTabs
import com.flowfoundation.wallet.page.window.WindowFrame

internal fun browserInstance() = WindowFrame.browserContainer()?.children?.firstOrNull() as? Browser

internal fun attachBrowser(activity: Activity, url: String? = null, params: BrowserParams) {
    val browserContainer = WindowFrame.browserContainer() ?: return
    if (browserContainer.childCount == 0) {
        val browser = Browser(activity)
        browserContainer.addView(browser)
        with(browser) {
            url?.let { loadUrl(url) }
            open(params)
        }
    }
}

fun releaseBrowser() {
    clearBrowserTabs()
    browserInstance()?.onRelease()
    WindowFrame.browserContainer()?.removeAllViews()
}

