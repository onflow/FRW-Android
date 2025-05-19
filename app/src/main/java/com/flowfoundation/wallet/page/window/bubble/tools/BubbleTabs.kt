package com.flowfoundation.wallet.page.window.bubble.tools

import com.flowfoundation.wallet.page.browser.tools.BrowserTab
import com.flowfoundation.wallet.page.browser.tools.popBrowserTab
import com.flowfoundation.wallet.page.window.bubble.attachBubble
import com.flowfoundation.wallet.page.window.bubble.bubbleViewModel
import com.flowfoundation.wallet.page.window.bubble.model.BubbleItem
import com.flowfoundation.wallet.page.window.bubble.releaseBubble
import com.flowfoundation.wallet.utils.uiScope

private val tabs = mutableListOf<BubbleItem>()

fun pushBubbleStack(data: Any, onPushed: (() -> Unit)? = null) {
    uiScope {
        tabs.removeAll { it.data == data }
        tabs.add(BubbleItem(data))
        attachBubble()
        bubbleViewModel()?.onTabChange()
        onPushed?.invoke()
    }
}

fun popBubbleStack(data: Any) {
    uiScope {
        tabs.removeAll { it.data == data }
        if (data is BrowserTab) {
            popBrowserTab(data.id)
        }
        bubbleViewModel()?.onTabChange()
        if (tabs.isEmpty()) {
            releaseBubble()
        }
    }
}

fun updateBubbleStack(data: Any) {
    uiScope { bubbleViewModel()?.onTabChange() }
}

fun inBubbleStack(data: Any): Boolean = tabs.toList().firstOrNull { it.data == data } != null

fun bubbleTabs() = tabs.toList()

fun clearBubbleTabs() {
    tabs.toList().forEach {
        popBubbleStack(it.data)
    }
}