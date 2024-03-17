package com.flowfoundation.wallet.widgets.floatwindow.widgets

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.flowfoundation.wallet.databinding.LayoutWindowRemoveBinding
import com.flowfoundation.wallet.utils.ScreenUtils
import com.flowfoundation.wallet.utils.extensions.rect
import com.flowfoundation.wallet.utils.safeRun
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.DraggableLayout
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class WindowRemoveLayout(
    private val root: ViewGroup,
    private val windowView: View,
    private val onRemove: () -> Unit,
) : DraggableLayout.OnDragListener {

    private val binding = LayoutWindowRemoveBinding.inflate(LayoutInflater.from(root.context), root, false)

    private val size by lazy { ScreenUtils.getScreenWidth() * 0.45f }

    init {
        root.addView(binding.root, 0)
        with(binding.root) {
            with(layoutParams) {
                width = size.toInt()
                height = size.toInt()
            }
            translationX = size
            translationY = size
        }
    }

    override fun onDrag(originX: Float, originY: Float, x: Float, y: Float) {
        with(binding.root) {
            val translation = min(size, max(0f, translationX - abs(originY - y)))
            translationX = translation
            translationY = translation
            alpha = min(1.0f, (size - translation) / size)
        }
    }

    override fun onDragEnd() {
        with(binding.root) {
            ObjectAnimator.ofFloat(this, "translationX", translationX, size).apply { duration = 200 }.start()
            ObjectAnimator.ofFloat(this, "translationY", translationY, size).apply { duration = 200 }.start()
            ObjectAnimator.ofFloat(this, "alpha", 1.0f, 0.0f).apply { duration = 200 }.start()
        }

        if (binding.root.rect().contains(windowView.rect())) {
            uiScope {
                delay(200)
                safeRun { onRemove.invoke() }
            }
        }
    }

    override fun onDragStart() {
    }
}