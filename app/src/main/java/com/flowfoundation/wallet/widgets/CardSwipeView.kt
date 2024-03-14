package com.flowfoundation.wallet.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.view.children
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.flowfoundation.wallet.utils.logd
import kotlin.math.pow

class CardSwipeView : MotionLayout {

    private var downX = 0.0f
    private var downY = 0.0f

    private var onClickListener: OnClickListener? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int)
      : super(context, attrs, defStyleAttr)


    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        requestDisallowInterceptTouchEvent(true)
        findSwipeRefreshLayout(this)?.isEnabled = ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL
        if (ev.action == MotionEvent.ACTION_DOWN) {
            downX = ev.x
            downY = ev.y
        }

        if (ev.action == MotionEvent.ACTION_UP) {
            val distance = (ev.x - downX).pow(2) + (ev.y - downY).pow(2)
            logd("CardSwipeView", "distance:$distance")
            if (distance < 36) {
                onClickListener?.onClick(this)
            }
        }

        return super.dispatchTouchEvent(ev)
    }

    override fun setOnClickListener(l: OnClickListener?) {
        this.onClickListener = l
    }

    private fun findSwipeRefreshLayout(view: View): SwipeRefreshLayout? {
        if (view.parent == null) return null
        if (view.parent is SwipeRefreshLayout) return (view.parent as SwipeRefreshLayout)

        return findSwipeRefreshLayout(view.parent as View)
    }

    fun refresh() {
        children.forEach { it.visibility = VISIBLE }
    }
}