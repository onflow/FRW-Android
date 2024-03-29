package com.flowfoundation.wallet.page.evm.widget

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.text.style.ReplacementSpan
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.extensions.res2color
import kotlin.math.roundToInt


class EvmTitleSpan: ReplacementSpan() {

    private var mSize: Int = 0

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        mSize = paint.measureText(text, start, end).roundToInt()
        return mSize
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val startColor = R.color.flow_evm_start_color.res2color()
        val endColor = R.color.flow_evm_end_color.res2color()
        val linear = LinearGradient(mSize.toFloat(), 0f, 0f, 0f, intArrayOf(startColor, endColor),
            floatArrayOf(0f, 1.0f), Shader.TileMode.REPEAT)
        paint.setShader(linear)
        canvas.drawText(text ?: "", start, end, x, y.toFloat(), paint)
    }
}