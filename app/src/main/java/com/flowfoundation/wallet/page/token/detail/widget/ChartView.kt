package com.flowfoundation.wallet.page.token.detail.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import com.github.mikephil.charting.charts.LineChart

class ChartView : FrameLayout {

    val chartView = LineChart(context)

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)

    init {
        addView(chartView)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        chartView.requestDisallowInterceptTouchEvent(true)
        return super.dispatchTouchEvent(ev)
    }
}