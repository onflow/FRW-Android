package com.flowfoundation.wallet.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.page.nft.nftlist.findSwipeRefreshLayout

class FocusRecyclerView : RecyclerView {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) {
            requestDisallowInterceptTouchEvent(false)
            findSwipeRefreshLayout(this)?.isEnabled = true
        } else {
            requestDisallowInterceptTouchEvent(false)
            findSwipeRefreshLayout(this)?.isEnabled = false
        }
        return super.dispatchTouchEvent(ev)
    }
}