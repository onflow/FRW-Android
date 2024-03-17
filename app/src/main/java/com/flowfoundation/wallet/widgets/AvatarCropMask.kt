package com.flowfoundation.wallet.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.res2color
import kotlin.math.min

class AvatarCropMask : View {

    private val paint by lazy {
        Paint().apply {
            color = R.color.black_50.res2color()
            isAntiAlias = true
            style = Paint.Style.FILL
        }
    }

    private val padding = 18.dp2px()

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0) {
            return
        }
        val path = Path().apply {
            addCircle(width / 2f, height / 2f, min(width, height) / 2f - padding, Path.Direction.CW)
        }
        canvas.clipOutPath(path)

        canvas.drawRect(Rect(0, 0, width, height), paint)
    }
}