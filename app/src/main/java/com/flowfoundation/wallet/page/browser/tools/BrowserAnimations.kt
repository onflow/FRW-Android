package com.flowfoundation.wallet.page.browser.tools

import android.animation.ValueAnimator
import android.graphics.Point
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.Fade
import androidx.transition.Scene
import androidx.transition.TransitionManager
import com.zackratos.ultimatebarx.ultimatebarx.navigationBarHeight
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.LayoutBrowserInputBinding
import com.flowfoundation.wallet.page.window.bubble.bubbleView
import com.flowfoundation.wallet.utils.ScreenUtils
import com.flowfoundation.wallet.utils.extensions.*
import kotlin.math.sqrt

private val screenHeight by lazy { ScreenUtils.getScreenHeight() }

fun shrinkWebView(webviewContainer: ViewGroup) {
    startWebViewAnimation(webviewContainer, false)
}

fun expandWebView(webviewContainer: ViewGroup) {
    startWebViewAnimation(webviewContainer, true)
}

fun startSearchBoxAnimation(binding: LayoutBrowserInputBinding, fromPosition: Point) {
    ValueAnimator.ofFloat(0.0f, 1.0f).apply {
        duration = 500
        interpolator = FastOutSlowInInterpolator()
        addUpdateListener {
            val progress = it.animatedValue as Float
            binding.updateInputPosition(fromPosition, progress)
            binding.updateInputAlpha(progress)
            binding.updateInputChildVisible(progress)
        }
        doOnEnd {
            binding.searchBox.cancelWrapper.setVisible()
            binding.searchBox.exploreSearchBox.root.setVisible(false)
        }
        start()
    }
}

private fun LayoutBrowserInputBinding.updateInputPosition(fromPosition: Point, progress: Float) {
    if (root.paddingBottom == 0) {
        return
    }
    val distance =
        screenHeight - fromPosition.y - root.paddingBottom - navigationBarHeight - 52.dp2px() - R.dimen.browser_input_vertical_padding.res2pix()
    with(inputWrapper.layoutParams as ViewGroup.MarginLayoutParams) {
        bottomMargin = (distance - distance * progress).toInt()
        inputWrapper.layoutParams = this
    }
}

private fun LayoutBrowserInputBinding.updateInputAlpha(progress: Float) {
    root.background.alpha = (255 * progress).toInt()
    searchBox.inputView.alpha = progress
    recyclerView.background.alpha = (255 * progress).toInt()
}

private fun LayoutBrowserInputBinding.updateInputChildVisible(progress: Float) {
    with(searchBox) {
        exploreSearchBox.root.alpha = 1 - progress
        cancelWrapper.alpha = progress
        cancelWrapper.setVisible(true)
        root.layoutParams.apply {
            height = (52 - (52 - 48) * progress).dp2px().toInt()
            root.layoutParams = this
        }
    }
}

private fun startWebViewAnimation(webviewContainer: ViewGroup, isExpand: Boolean) {
    if ((webviewContainer.isVisible() && isExpand) || (!webviewContainer.isVisible() && !isExpand)) {
        return
    }

    val bubbleView = bubbleView() ?: return

    TransitionManager.go(Scene(webviewContainer.parent as ViewGroup), Fade().apply { duration = 150 })

    bubbleView.post {
        val bubbleLocation = bubbleView.location()

        val centerX = bubbleLocation.x + bubbleView.width / 2
        val centerY = bubbleLocation.y + bubbleView.height / 2

        val width = webviewContainer.width
        val height = webviewContainer.height
        val radius = sqrt((width * width + height * height).toFloat()) / 2
        with(
            ViewAnimationUtils.createCircularReveal(
                webviewContainer,
                centerX,
                centerY,
                if (isExpand) 0f else radius,
                if (isExpand) radius else 0f
            )
        ) {
            duration = 250
            interpolator = AccelerateDecelerateInterpolator()
            doOnEnd {
                webviewContainer.setVisible(isExpand)
                if (!isExpand) {
                    removeWebview()
                }
            }
            start()
            if (isExpand) {
                webviewContainer.setVisible()
            }
        }
    }
}

