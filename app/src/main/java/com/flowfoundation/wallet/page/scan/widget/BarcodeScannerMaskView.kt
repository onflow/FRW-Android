package com.flowfoundation.wallet.page.scan.widget

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import com.journeyapps.barcodescanner.ViewfinderView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.res2color

class BarcodeScannerMaskView(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewfinderView(context, attrs) {

    private var clipRect: RectF? = null

    private val pointColor by lazy { R.color.salmon_primary.res2color() }

    private val pointSize = 5.dp2px()

    private val maskPaint by lazy {
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
        }
    }

    private val pointPaint by lazy {
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = pointColor
        }
    }

    private var clipPath: Path? = null

    override fun onDraw(canvas: Canvas) {
        if (width == 0) {
            return
        }

        clipRect = clipRect ?: generateClipRect()
        clipPath = clipPath ?: createClipPath()

        canvas.save()
        val path = clipPath!!
        canvas.clipOutPath(path)

        maskPaint.color = maskColor
        canvas.drawPaint(maskPaint)
        canvas.restore()

        drawResultPoints(canvas)

        clipRect?.let { rect ->
            postInvalidateDelayed(
                ANIMATION_DELAY,
                rect.left.toInt(),
                rect.top.toInt(),
                rect.right.toInt(),
                rect.bottom.toInt(),
            )
        }
    }

    private fun drawResultPoints(canvas: Canvas) {
        pointPaint.alpha = CURRENT_POINT_OPACITY / 2
        if (lastPossibleResultPoints.isNotEmpty()) {
            lastPossibleResultPoints.filter { clipRect!!.contains(it.x, it.y) }.forEach { point ->
                canvas.drawCircle(point.x, point.y, pointSize / 2, pointPaint)
            }
            lastPossibleResultPoints.clear()
        }

        pointPaint.alpha = CURRENT_POINT_OPACITY
        if (possibleResultPoints.isNotEmpty()) {
            possibleResultPoints.filter { clipRect!!.contains(it.x, it.y) }.forEach { point ->
                canvas.drawCircle(point.x, point.y, pointSize, pointPaint)
            }

            lastPossibleResultPoints.clear()
            lastPossibleResultPoints.addAll(possibleResultPoints)
            possibleResultPoints.clear()
        }
    }

    private fun generateClipRect() = RectF().apply {
        val r = width / 2 * 0.7f
        set(width / 2 - r, height / 2 - r, width / 2 + r, height / 2 + r)
    }

    private fun createClipPath() = Path().apply {
        val round = 30.dp2px()
        addRoundRect(clipRect!!, round, round, Path.Direction.CCW)
    }
}