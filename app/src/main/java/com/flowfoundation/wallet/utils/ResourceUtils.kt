package com.flowfoundation.wallet.utils

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat


fun drawableResToBitmap(context: Context, @DrawableRes res: Int, @ColorInt tint: Int = 0): Bitmap? {
    val sourceBitmap = ContextCompat.getDrawable(context, res)?.toBitmap()?.copy(Bitmap.Config.ARGB_8888, true) ?: return null
    if (tint != 0) {
        val paint = Paint().apply { colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN) }
        Canvas(sourceBitmap).drawBitmap(sourceBitmap, 0f, 0f, paint)
    }
    return sourceBitmap
}

fun Drawable.toBitmap(): Bitmap? {
    if (this is BitmapDrawable) {
        return this.bitmap
    }
    val bitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}