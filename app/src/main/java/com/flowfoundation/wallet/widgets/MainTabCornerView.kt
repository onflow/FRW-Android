package com.flowfoundation.wallet.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.res2color

class MainTabCornerView : View {

    private var clipPath: Path? = null

    private val paint by lazy {
        Paint().apply {
            color = R.color.deep_bg.res2color()
            isAntiAlias = true
            style = Paint.Style.FILL
        }
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)

    override fun onDraw(canvas: Canvas) {
        if (width == 0) {
            return
        }
        canvas.save()
        canvas.clipOutPath(getClipPath())
        paint.color = R.color.deep_bg.res2color()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        canvas.restore()
    }

    private fun getClipPath(): Path {
        return clipPath ?: Path().apply {
            addRoundRect(rect(), 24.dp2px(), 24.dp2px(), Path.Direction.CW)
            addRect(RectF(0f, 0f, width.toFloat(), height.toFloat() / 2), Path.Direction.CW)
            clipPath = this
        }
    }

    private fun rect() = RectF(0f, 0f, width.toFloat(), height.toFloat())
}