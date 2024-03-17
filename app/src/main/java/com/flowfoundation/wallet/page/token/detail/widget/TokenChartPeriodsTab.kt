package com.flowfoundation.wallet.page.token.detail.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.children
import androidx.core.view.forEach
import com.google.android.material.button.MaterialButton
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.page.token.detail.Period
import com.flowfoundation.wallet.utils.extensions.res2color

class TokenChartPeriodsTab : FrameLayout {
    private var onTabClickListener: ((tab: Period) -> Unit)? = null

    private val tabContainer by lazy { LayoutInflater.from(context).inflate(R.layout.widget_token_chart_periods_tab, this, false) as ViewGroup }

    private var tab = Period.DAY.value

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)

    init {
        addView(tabContainer)

        updateTabState()

        tabContainer.children.forEach { tabView ->
            tabView.setOnClickListener {
                val tag = it.tag as String
                if (tab == tag) {
                    return@setOnClickListener
                }
                tab = tag
                onTabClickListener?.invoke(Period.fromValue(tag))
                updateTabState()
            }
        }
    }

    fun setOnTabClickListener(listener: (tab: Period) -> Unit) {
        this.onTabClickListener = listener
    }

    fun selectTab(tab: Period) {
        this.tab = tab.value
        onTabClickListener?.invoke(tab)
        updateTabState()
    }

    private fun updateTabState() {
        tabContainer.forEach { tabView ->
            val tag = tabView.tag as String
            (tabView as MaterialButton).backgroundTintList =
                ColorStateList.valueOf(if (tag == tab) R.color.outline.res2color() else R.color.background.res2color())
        }
    }
}