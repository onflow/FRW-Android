package com.flowfoundation.wallet.widgets.easyfloat.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.flowfoundation.wallet.utils.extensions.isVisible
import com.flowfoundation.wallet.widgets.easyfloat.data.FloatConfig
import com.flowfoundation.wallet.widgets.easyfloat.interfaces.OnFloatTouchListener
import com.flowfoundation.wallet.widgets.easyfloat.utils.InputMethodUtils

/**
 * @author: liuzhenfeng
 * @function: 系统浮窗的父布局，对touch事件进行了重新分发
 * @date: 2019-07-10  14:16
 */
@SuppressLint("ViewConstructor")
internal class ParentFrameLayout(
    context: Context,
    private val config: FloatConfig,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var touchListener: OnFloatTouchListener? = null
    var layoutListener: OnLayoutListener? = null
    private var isCreated = false

    private val ignoreDragViewList by lazy { generateIgnoreViews() }

    private val forceDragViewList by lazy { generateForceDragViews() }

    private var ignoreDragEvent = false

    private var forceDragEvent = false

    init {
        if (config.hardKeyEventEnable) {
            setBackgroundColor(Color.TRANSPARENT)
            // 返回键需要
            isFocusableInTouchMode = true
            isFocusable = true
            requestFocus()
        }
    }

    private fun generateIgnoreViews(): List<View> {
        return config.ignoreDragViewList.mapNotNull { findViewById(it) }
    }

    private fun generateForceDragViews(): List<View> {
        return config.forceDragViewList.mapNotNull { findViewById(it) }
    }

    // 布局绘制完成的接口，用于通知外部做一些View操作，不然无法获取view宽高
    interface OnLayoutListener {
        fun onLayout()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // 初次绘制完成的时候，需要设置对齐方式、坐标偏移量、入场动画
        if (!isCreated) {
            isCreated = true
            layoutListener?.onLayout()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            forceDragEvent = isContainsDragEvent(forceDragViewList, event)
            ignoreDragEvent = isContainsDragEvent(ignoreDragViewList, event)
        }
        if (ignoreDragEvent) {
            return super.dispatchTouchEvent(event)
        }

        touchListener?.onTouch(event)

        if (forceDragEvent && config.isDragging) {
            if (event.action == MotionEvent.ACTION_UP) {
                event.action = MotionEvent.ACTION_CANCEL
            }
        }

        super.dispatchTouchEvent(event)

        return config.isDragging && config.dragEnable
    }

    private fun isContainsDragEvent(views: List<View>, event: MotionEvent): Boolean {
        return views.findLast {
            if (it.isVisible()) {
                val location = intArrayOf(0, 0)
                it.getLocationInWindow(location)
                val rect = Rect(location[0], location[1], location[0] + it.width, location[1] + it.height)
                rect.contains(event.x.toInt(), event.y.toInt())
            } else {
                false
            }
        } != null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK) {
            config.floatCallbacks?.builder?.backKeyPressed?.invoke()
        }
        return super.dispatchKeyEvent(event)
    }


    /**
     * 按键转发到视图的分发方法，在这里关闭输入法
     */
    override fun dispatchKeyEventPreIme(event: KeyEvent?): Boolean {
        if (config.hasEditText && event?.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK) {
            InputMethodUtils.closedInputMethod(config.floatTag)
        }
        return super.dispatchKeyEventPreIme(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        config.callbacks?.dismiss()
        config.floatCallbacks?.builder?.dismiss?.invoke()
    }
}