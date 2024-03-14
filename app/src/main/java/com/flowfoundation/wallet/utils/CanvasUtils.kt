package com.flowfoundation.wallet.utils

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Rect


/**
 * @author wangkai
 */
// 获取文字宽度
fun getTextWidth(text: CharSequence?, textSize: Float): Int {
    if (text.isNullOrEmpty()) return 0
    val rect = Rect()
    Paint().apply {
        this.textSize = textSize
    }.getTextBounds(text.toString(), 0, text.length, rect)
    return rect.width()
}

// 获取文字宽度
fun getTextHeight(text: CharSequence?, textSize: Float): Int {
    return getTextHeight(text, Paint().apply {
        this.textSize = textSize
    })
}

// 获取文字宽度
fun getTextHeight(text: CharSequence?, paint: Paint): Int {
    if (text.isNullOrEmpty()) return 0
    val rect = Rect()
    paint.getTextBounds(text.toString(), 0, text.length, rect)
    return rect.height()
}

fun Path.lastPoint(): FloatArray? {
    val pm = PathMeasure(this, false)
    if (pm.length == 0f) {
        return null
    }

    val point = floatArrayOf(0f, 0f)
    pm.getPosTan(pm.length, point, null)
    return point
}

fun Path.firstPoint(): FloatArray? {
    val pm = PathMeasure(this, false)
    if (pm.length == 0f) {
        return null
    }
    val point = floatArrayOf(0f, 0f)
    pm.getPosTan(0f, point, null)
    return point
}
