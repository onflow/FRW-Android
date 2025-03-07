package com.flowfoundation.wallet.utils

import android.graphics.Paint
import android.graphics.Rect


/**
 * @author wangkai
 */

// 获取文字宽度
fun getTextHeight(text: CharSequence?, paint: Paint): Int {
    if (text.isNullOrEmpty()) return 0
    val rect = Rect()
    paint.getTextBounds(text.toString(), 0, text.length, rect)
    return rect.height()
}

