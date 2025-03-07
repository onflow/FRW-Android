package com.flowfoundation.wallet.widgets.progress

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.extensions.dp2px

class StepProgressBar : FrameLayout {

    private var step = 7

    private var wrapper: ViewGroup

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)

    init {
        LayoutInflater.from(context).inflate(R.layout.widget_step_progress_bar, this)
        wrapper = findViewById(R.id.step_wrapper)
    }

    fun setup(step: Int, progress: Int) {
        if (wrapper.childCount == 0) {
            (0 until step).forEach { index ->
                val view = LayoutInflater.from(context).inflate(R.layout.widget_step_progress_bar_item, wrapper, false)
                wrapper.addView(view)
                (view.layoutParams as? LinearLayout.LayoutParams)?.apply {
                    if (index != 0) {
                        marginStart = 2.dp2px().toInt()
                        view.layoutParams = this
                    }
                }
            }
        }
        setProgress(progress)
    }

    fun setProgress(progress: Int) {
        if (progress < 0 || progress > step || wrapper.childCount == 0) return
        (0 until step).forEach { index ->
            wrapper.getChildAt(index).setBackgroundResource(if (index + 1 <= progress) R.color.salmon_primary else R.color.transparent)
        }
    }
}