package com.flowfoundation.wallet.widgets.floatwindow

import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import com.flowfoundation.wallet.utils.ScreenUtils

internal fun createParams(config: FloatWindowConfig): ViewGroup.MarginLayoutParams {
    val width = when {
        config.width != 0 -> config.width
        config.widthMatchParent -> WindowManager.LayoutParams.MATCH_PARENT
        else -> WindowManager.LayoutParams.WRAP_CONTENT
    }
    val height = when {
        config.height != 0 -> config.height
        config.heightMatchParent -> WindowManager.LayoutParams.MATCH_PARENT
        config.immersionStatusBar && config.heightMatchParent -> ScreenUtils.getScreenHeight()
        else -> WindowManager.LayoutParams.WRAP_CONTENT
    }

    return ViewGroup.MarginLayoutParams(width, height).apply {
        if (config.gravity and Gravity.HORIZONTAL_GRAVITY_MASK == Gravity.START) {
            marginStart = config.offsetX
        } else {
            marginEnd = config.offsetX
        }

        if (config.gravity and Gravity.VERTICAL_GRAVITY_MASK == Gravity.TOP) {
            topMargin = config.offsetY
        } else {
            bottomMargin = config.offsetY
        }
    }
}