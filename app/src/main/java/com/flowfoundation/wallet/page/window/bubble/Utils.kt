package com.flowfoundation.wallet.page.window.bubble

import com.flowfoundation.wallet.utils.extensions.location

fun bubbleLocation() = bubbleInstance()?.binding()?.floatBubble?.location()

fun bubbleView() = bubbleInstance()?.binding()?.floatBubble

fun bubbleViewModel() = bubbleInstance()?.viewModel()

fun onBubbleClick() {
    bubbleViewModel()?.showFloatTabs()
}