package com.flowfoundation.wallet.widgets

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView


/**
 * use for contained in NestedScrollView
 */
class NestedRecyclerView : RecyclerView {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)

    init {
//        isNestedScrollingEnabled = false
    }

//    override fun onAttachedToWindow() {
//        super.onAttachedToWindow()
//        with(layoutParams) {
//            height = ScreenUtils.getScreenHeight()
//            layoutParams = this
//        }
//    }

}