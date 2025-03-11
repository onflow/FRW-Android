package com.flowfoundation.wallet.utils

import android.animation.*
import android.view.View
import android.view.animation.Interpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

fun createScaleObjectAnimation(
    view: View,
    from: Float,
    to: Float,
    duration: Long = 200,
    interpolator: Interpolator = FastOutSlowInInterpolator()
): ObjectAnimator {
    val x = PropertyValuesHolder.ofFloat("scaleX", from, to)
    val y = PropertyValuesHolder.ofFloat("scaleY", from, to)
    return ObjectAnimator.ofPropertyValuesHolder(view, x, y).apply {
        this.duration = duration
        this.interpolator = interpolator
    }
}
