package com.flowfoundation.wallet.page.window.bubble

class BubbleViewModel {

    internal var onShowFloatTabs: (() -> Unit)? = null
    internal var onHideFloatTabs: (() -> Unit)? = null

    internal var onTabChange: (() -> Unit)? = null

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