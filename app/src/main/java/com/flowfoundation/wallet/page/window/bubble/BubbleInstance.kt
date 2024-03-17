package com.flowfoundation.wallet.page.window.bubble

import androidx.core.view.children
import com.flowfoundation.wallet.page.window.WindowFrame
import com.flowfoundation.wallet.page.window.bubble.tools.clearBubbleTabs


internal fun bubbleInstance() = WindowFrame.bubbleContainer()?.children?.firstOrNull() as? Bubble

fun attachBubble() {
    val bubbleContainer = WindowFrame.bubbleContainer() ?: return
    if (bubbleContainer.childCount == 0) {
        val bubble = Bubble(bubbleContainer.context)
        bubbleContainer.addView(bubble)
    }
}

fun releaseBubble() {
    clearBubbleTabs()
    WindowFrame.bubbleContainer()?.removeAllViews()
}

