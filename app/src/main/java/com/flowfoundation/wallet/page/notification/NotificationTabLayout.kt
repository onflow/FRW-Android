package com.flowfoundation.wallet.page.notification

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.setMargins
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.extensions.dp2px

class NotificationTabLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private var maxCount = 0

    init {
        orientation = HORIZONTAL
    }

    fun setMaxCount(count: Int) {
        maxCount = count
        removeAllViews()
        val layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            setMargins(4f.dp2px().toInt())
        }
        for (i in 0 until count) {
            val imageView = ImageView(context).apply {
                setImageResource(
                    if (i == 0) R.drawable.bg_indicator_selected else R.drawable.bg_indicator_normal
                )
                this.layoutParams = layoutParams
            }
            addView(imageView)
        }
    }

    fun onTabSelected(position: Int) {
        if (maxCount == 0) return
        val childPosition = position % maxCount
        for (i in 0 until childCount) {
            val imageView = getChildAt(i) as ImageView
            if (i == childPosition) {
                imageView.setImageResource(R.drawable.bg_indicator_selected)
            } else {
                imageView.setImageResource(R.drawable.bg_indicator_normal)
            }
        }
    }
}