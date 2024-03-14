package com.flowfoundation.wallet.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent

class FrozenCheckbox : androidx.appcompat.widget.AppCompatCheckBox {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        return false
    }
}