package com.flowfoundation.wallet.widgets

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MenuItem
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.page.main.updateIcons
import com.flowfoundation.wallet.utils.extensions.dp2px
import kotlin.math.abs

private const val DEFAULT_SCALE = 1f
private const val MAX_SCALE = 2f
private const val BASE_DURATION = 200L
private const val VARIABLE_DURATION = 200L

class BottomNavigationViewWithIndicator : BottomNavigationView,
    BottomNavigationView.OnNavigationItemSelectedListener {

    private var externalSelectedListener: OnNavigationItemSelectedListener? = null
    private var animator: ValueAnimator? = null

    private val indicator = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.accent_green)
    }

    private val bottomOffset = 4.dp2px()
    private val defaultSize = 20.dp2px()

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int)
      : super(context, attrs, defStyleAttr)

    init {
        super.setOnNavigationItemSelectedListener(this)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        if (externalSelectedListener?.onNavigationItemSelected(item) != false) {
            onItemSelected(item.itemId)

            post {
                updateIcons()
            }
            return true
        }
        return false
    }

    override fun setOnNavigationItemSelectedListener(listener: OnNavigationItemSelectedListener?) {
        externalSelectedListener = listener
    }

    override fun setSelectedItemId(itemId: Int) {
        super.setSelectedItemId(itemId)
        onItemSelected(itemId)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post {
            onItemSelected(selectedItemId, false)
            updateIcons()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelAnimator(setEndValues = true)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (isLaidOut) {
            val cornerRadius = indicator.height() / 2f
            canvas.drawRoundRect(indicator, cornerRadius, cornerRadius, paint)
        }
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {}

    override fun setPaddingRelative(start: Int, top: Int, end: Int, bottom: Int) {
    }

    fun updateIndicatorColor(color: Int) {
        paint.color = color
        invalidate()
    }

    private fun onItemSelected(itemId: Int, animate: Boolean = true) {
        if (!isLaidOut) return

        cancelAnimator(setEndValues = false)

        val itemView = findViewById<View>(itemId) ?: return
        val toCenterX = itemView.centerX
        val fromCenterX = indicator.centerX()
        val fromScale = indicator.width() / defaultSize

        animator = ValueAnimator.ofFloat(fromScale, MAX_SCALE, DEFAULT_SCALE).apply {
            addUpdateListener {
                val progress = it.animatedFraction
                val distanceTravelled = fromCenterX + (toCenterX - fromCenterX) * progress

                val scale = it.animatedValue as Float
                val indicatorWidth = defaultSize * scale

                val left = distanceTravelled - indicatorWidth / 2f
                val right = distanceTravelled + indicatorWidth / 2f
                indicator.set(left, 0f, right, bottomOffset)
                invalidate()
            }

            interpolator = OvershootInterpolator()

            val distanceToMove = abs(fromCenterX - itemView.centerX)
            duration = if (animate) calculateDuration(distanceToMove) else 0L

            start()
        }
    }

    private fun calculateDuration(distance: Float) =
        (BASE_DURATION + VARIABLE_DURATION * (distance / width).coerceIn(0f, 1f)).toLong()

    private val View.centerX get() = left + width / 2f

    private fun cancelAnimator(setEndValues: Boolean) = animator?.let {
        if (setEndValues) {
            it.end()
        } else {
            it.cancel()
        }
        it.removeAllUpdateListeners()
        animator = null
    }
}