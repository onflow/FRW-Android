package com.flowfoundation.wallet.page.browser.widgets

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.transition.Fade
import androidx.transition.Scene
import androidx.transition.TransitionManager
import com.flowfoundation.wallet.utils.extensions.setVisible

class BrowserProgressView : View {

    private var animator: ValueAnimator? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int)
      : super(context, attrs, defStyleAttr)


    fun setProgress(progress: Float) {
        startAnimation(progress)
        TransitionManager.go(Scene(parent as ViewGroup), Fade().apply {
            duration = if (progress == 1f) 400 else 200
            startDelay = if (progress == 1f) 400 else -1
        })
        postDelayed({ setVisible(progress != 1f) }, if (progress == 1f) 400 else 0)
    }

    private fun startAnimation(toProgress: Float) {
        animator?.cancel()
        animator = createAnimation(toProgress)
    }

    private fun createAnimation(toProgress: Float): ValueAnimator {
        return ValueAnimator.ofFloat(progress(), toProgress).apply {
            duration = 300
            addUpdateListener {
                val p = it.animatedValue as Float
                updateProgress(p)
            }
            start()
        }
    }

    private fun updateProgress(progress: Float) {
        val params = layoutParams as ConstraintLayout.LayoutParams
        params.matchConstraintPercentWidth = progress
        requestLayout()
    }

    private fun progress() = (layoutParams as ConstraintLayout.LayoutParams).matchConstraintPercentWidth
}