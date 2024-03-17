package com.flowfoundation.wallet.utils.extensions

import android.graphics.Canvas
import android.graphics.Paint
import com.flowfoundation.wallet.utils.getTextHeight

/**
 * @author wangkai
 */

/**
 * 绘制文字
 * @param y 文字上方位置
 */
fun Canvas.drawTextExt(text: String, x: Float, y: Float, paint: Paint) {
    /**
     * bounds: text 绘制的区域
     * fontMetricsInt： paint.fontMetricsInt
     * draw text 基准线公式：(bounds.bottom + bounds.top - fontMetricsInt.bottom - fontMetricsInt.top) / 2f
     */
    val fontMetricsInt = paint.fontMetricsInt

    val baseline = (y + getTextHeight(text, paint) - fontMetricsInt.bottom - fontMetricsInt.top) / 2f
    drawText(text, x, baseline, paint)
}
