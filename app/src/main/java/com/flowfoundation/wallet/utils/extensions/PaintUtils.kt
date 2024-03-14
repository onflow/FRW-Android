package com.flowfoundation.wallet.utils.extensions

import android.graphics.Paint
import android.graphics.Rect


fun Paint.getTextWidth(text: CharSequence): Int {
    val rect = Rect()
    getTextBounds(text.toString(), 0, text.length, rect)
    return rect.width()
}