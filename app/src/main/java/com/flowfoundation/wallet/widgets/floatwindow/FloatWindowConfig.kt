package com.flowfoundation.wallet.widgets.floatwindow

import android.app.Activity
import android.view.Gravity
import android.view.View
import kotlin.reflect.KClass

data class FloatWindowConfig(
    var gravity: Int = Gravity.START or Gravity.TOP,
    var offsetX: Int = 0,
    var offsetY: Int = 0,
    var layoutId: Int = 0,
    var contentView: View? = null,
    var immersionStatusBar: Boolean = false,
    var hardKeyEventEnable: Boolean = false,
    var isTouchEnable: Boolean = true,
    var widthMatchParent: Boolean = false,
    var heightMatchParent: Boolean = false,
    var width: Int = 0,
    var height: Int = 0,
    var tag: String = "",
    var disableAnimation: Boolean = false,
    var isFullScreen: Boolean = false,
    var ignorePage: List<KClass<out Activity>> = emptyList()
)
