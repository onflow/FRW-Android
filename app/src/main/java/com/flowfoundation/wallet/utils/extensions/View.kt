package com.flowfoundation.wallet.utils.extensions

import android.graphics.*
import android.view.View
import android.view.ViewGroup
import androidx.transition.Fade
import androidx.transition.Scene
import androidx.transition.TransitionManager

/**
 * @author John
 * @since 2018-12-15 16:04
 */

fun View.isVisible() = this.visibility == View.VISIBLE

fun View.gone() {
    this.visibility = View.GONE
}

fun View.visible() {
    this.visibility = View.VISIBLE
}

fun View.invisible() {
    this.visibility = View.INVISIBLE
}

fun View.setVisible(visible: Boolean = true, invisible: Boolean = false) {
    when {
        visible -> this.visibility = View.VISIBLE
        invisible -> this.visibility = View.INVISIBLE
        else -> this.visibility = View.GONE
    }
}

fun View.location(): Point {
    val location = IntArray(2)
    getLocationInWindow(location)
    return Point(location[0], location[1])
}

fun View.rect(): Rect {
    val location = location()
    return Rect(location.x, location.y, location.x + width, location.y + height)
}

fun ViewGroup.fadeTransition(duration: Long = 0) {
    TransitionManager.go(Scene(this), Fade().apply {
        if (duration > 0) {
            this.duration = duration
        }
    })
}

fun View.removeFromParent() {
    (parent as? ViewGroup)?.removeViewInLayout(this)
}
