package com.flowfoundation.wallet.widgets

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.util.AttributeSet

class BlurImageView : androidx.appcompat.widget.AppCompatImageView {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)

    init {
        val paint = Paint()
        paint.maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
        setLayerPaint(paint)
    }
}