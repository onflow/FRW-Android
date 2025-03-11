package com.flowfoundation.wallet.utils.extensions

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.utils.Env


//@ColorRes
fun Int.res2pix(): Int {
    return Env.getApp().resources.getDimensionPixelSize(this)
}

fun Int.res2dip(): Float {
    return Env.getApp().resources.getDimension(this) / Env.getApp().resources.displayMetrics.density
}

fun Int.dp2px(): Float {
    val scale = Resources.getSystem().displayMetrics.density
    return this * scale + 0.5f
}

//@ColorRes
fun Int.res2String(): String {
    return Env.getApp().getString(this)
}

@ColorInt
fun Int.res2color(context: Context? = null): Int {
    val ctx = context ?: (BaseActivity.getCurrentActivity() ?: Env.getApp())
    return ctx.getColor(this)
}

fun Int.colorStateList(context: Context? = null): ColorStateList? {
    val ctx = context ?: (BaseActivity.getCurrentActivity() ?: Env.getApp())
    return ContextCompat.getColorStateList(ctx, this)
}

fun Float.dp2px(): Float {
    val scale = Resources.getSystem().displayMetrics.density
    return this * scale + 0.5f
}

@ColorInt
fun Int.alpha(alpha: Float): Int {
    return Color.argb(
        (alpha * 255).toInt(),
        Color.red(this),
        Color.green(this),
        Color.blue(this)
    )
}