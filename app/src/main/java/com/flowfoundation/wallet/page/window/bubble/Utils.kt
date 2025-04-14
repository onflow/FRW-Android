package com.flowfoundation.wallet.page.window.bubble

fun bubbleView() = bubbleInstance()?.binding()?.floatBubble

fun bubbleViewModel() = bubbleInstance()?.viewModel()

fun onBubbleClick() {
    bubbleViewModel()?.showFloatTabs()
}