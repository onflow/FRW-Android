package com.flowfoundation.wallet.widgets.easyfloat.interfaces

import android.view.MotionEvent
import android.view.View

/**
 * @author: liuzhenfeng
 * @function: 通过Kotlin DSL实现接口回调效果
 * @date: 2019-08-26  17:06
 */
class FloatCallbacks {

    lateinit var builder: Builder

    inner class Builder {
        internal var createdResult: ((Boolean, String?, View?) -> Unit)? = null
        internal var show: ((View) -> Unit)? = null
        internal var hide: ((View) -> Unit)? = null
        internal var dismiss: (() -> Unit)? = null
        internal var touchEvent: ((View, MotionEvent) -> Unit)? = null
        internal var drag: ((View, MotionEvent) -> Unit)? = null
        internal var dragEnd: ((View) -> Unit)? = null
        internal var backKeyPressed: (() -> Unit)? = null

        fun show(action: (View) -> Unit) {
            show = action
        }

        fun hide(action: (View) -> Unit) {
            hide = action
        }

        fun dismiss(action: () -> Unit) {
            dismiss = action
        }

    }

}