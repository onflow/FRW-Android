package com.flowfoundation.wallet.utils

import android.animation.*
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Interpolator
import androidx.annotation.ColorInt
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.sackcentury.shinebuttonlib.listener.SimpleAnimatorListener

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

fun createAlphaObjectAnimation(
    view: View,
    from: Float,
    to: Float,
    duration: Long = 200,
    interpolator: Interpolator = FastOutSlowInInterpolator()
): ObjectAnimator {
    return ObjectAnimator.ofPropertyValuesHolder(view, PropertyValuesHolder.ofFloat("alpha", from, to)).apply {
        this.duration = duration
        this.interpolator = interpolator
    }
}

fun createColorAnimation(
    @ColorInt from: Int,
    @ColorInt to: Int,
    duration: Long = 200,
    interpolator: Interpolator = FastOutSlowInInterpolator(),
    autoStart: Boolean = false,
    callback: (color: Int) -> Unit
): ValueAnimator {
    return ValueAnimator.ofInt(from, to).apply {
        setEvaluator(ArgbEvaluator())
        addUpdateListener {
            val color = it.animatedValue as Int
            callback(color)
        }
        this.duration = duration
        this.interpolator = interpolator
        if (autoStart) {
            start()
        }
    }
}

fun createTranslationYAnimation(
    view: View,
    from: Int,
    to: Int,
    duration: Long = 200,
    interpolator: Interpolator = FastOutSlowInInterpolator()
): ObjectAnimator {
    return ObjectAnimator.ofFloat(view, "translationY", from.toFloat(), to.toFloat()).apply {
        this.duration = duration
        this.interpolator = interpolator
    }
}

inline fun Animator.addEndListener(crossinline onEnd: (animator: Animator) -> Unit = {}): Animator.AnimatorListener {
    val listener = object : SimpleAnimatorListener() {
        override fun onAnimationEnd(animation: Animator) = onEnd(animation)
    }
    addListener(listener)
    return listener
}

inline fun Animator.addStartListener(crossinline onStart: (animator: Animator) -> Unit = {}): Animator.AnimatorListener {
    val listener = object : SimpleAnimatorListener() {
        override fun onAnimationStart(animation: Animator) = onStart(animation)
    }
    addListener(listener)
    return listener
}

fun View.alphaIn(duration: Long) {
    startAnimation(AlphaAnimation(0f, 1f).apply { this.duration = duration })
}