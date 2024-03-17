package com.flowfoundation.wallet.page.window.bubble

import com.flowfoundation.wallet.page.browser.tools.BrowserTab

class BubbleViewModel {

    internal var onRemoveBrowserTab: ((BrowserTab) -> Unit)? = null

    internal var onShowFloatTabs: (() -> Unit)? = null
    internal var onHideFloatTabs: (() -> Unit)? = null

    internal var onTabChange: (() -> Unit)? = null

    fun popTab(tab: BrowserTab) {
        onRemoveBrowserTab?.invoke(tab)
    }

    fun showFloatTabs() {
        onShowFloatTabs?.invoke()
    }

    fun onHideFloatTabs() {
        onHideFloatTabs?.invoke()
    }

    fun onTabChange() {
        onTabChange?.invoke()
    }
}