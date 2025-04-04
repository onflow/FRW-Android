package com.flowfoundation.wallet.page.token.detail.presenter

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IFillFormatter
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.LayoutTokenDetailChartBinding
import com.flowfoundation.wallet.network.model.CryptowatchSummaryData
import com.flowfoundation.wallet.page.token.detail.Period
import com.flowfoundation.wallet.page.token.detail.Quote
import com.flowfoundation.wallet.page.token.detail.QuoteMarket
import com.flowfoundation.wallet.page.token.detail.TokenDetailViewModel
import com.flowfoundation.wallet.page.token.detail.model.TokenDetailChartModel
import com.flowfoundation.wallet.page.token.detail.widget.ChartMarker
import com.flowfoundation.wallet.page.token.detail.widget.TokenDetailMarketPopupMenu
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.formatNum
import com.flowfoundation.wallet.utils.formatPrice
import com.flowfoundation.wallet.utils.getQuoteMarket
import com.flowfoundation.wallet.utils.uiScope
import kotlin.math.absoluteValue


class TokenDetailChartPresenter(
    private val activity: AppCompatActivity,
    private val binding: LayoutTokenDetailChartBinding,
) : BasePresenter<TokenDetailChartModel> {
    private val viewModel by lazy { ViewModelProvider(activity)[TokenDetailViewModel::class.java] }

    private val chartView by lazy { binding.chartView.chartView }

    init {
        setupChartView()
        binding.chartPeriodTabs.setOnTabClickListener { viewModel.changePeriod(it) }
        binding.chartPeriodTabs.selectTab(Period.DAY)
        binding.dataFrom.setOnClickListener { TokenDetailMarketPopupMenu(it) { market -> setupMarket(market) }.show() }
        setupMarket()
    }

    private fun setupMarket(market: String? = null) {
        uiScope {
            val market = (market ?: getQuoteMarket()).lowercase()
            var icon = R.drawable.ic_market_binance
            var name = R.string.market_binance
            when (market) {
                QuoteMarket.kraken.value.lowercase() -> {
                    icon = R.drawable.ic_market_kraken
                    name = R.string.market_kraken
                }
                QuoteMarket.huobi.value.lowercase() -> {
                    icon = R.drawable.ic_market_huobi
                    name = R.string.market_huobi
                }
            }
            binding.marketIcon.setImageResource(icon)
            binding.marketView.setText(name)
        }
    }

    override fun bind(model: TokenDetailChartModel) {
        model.chartData?.let { updateChartData(it) }
        model.summary?.let { updateSummary(it) }
    }

    @SuppressLint("SetTextI18n")
    private fun updateSummary(summary: CryptowatchSummaryData.Result) {
        with(binding) {
            priceView.text = summary.price.last.formatPrice(includeSymbol = true)

            val isRise = summary.price.change.percentage >= 0
            quoteChangeWrapper.setVisible()
            quoteChangeWrapper.backgroundTintList =
                ColorStateList.valueOf(if (isRise) R.color.quote_up_opacity.res2color() else R.color.quote_down_opacity.res2color())
            quoteChangeIconView.setImageResource(if (isRise) R.drawable.ic_triangle_up else R.drawable.ic_triangle_down)
            quoteChangeView.setTextColor(if (isRise) R.color.quote_up.res2color() else R.color.quote_down.res2color())
            quoteChangeView.text = "${summary.price.change.percentage.absoluteValue.formatNum(2)}%"
        }
    }

    private fun updateChartData(quotes: List<Quote>) {
        val data = quotes.map { Entry(it.closeTime.toFloat(), it.closePrice) }
        if (chartView.data != null && chartView.data.dataSetCount > 0) {
            val dataSet = chartView.data.getDataSetByIndex(0) as LineDataSet
            dataSet.values = data
            chartView.data.notifyDataChanged()
            chartView.notifyDataSetChanged()
        } else {
            val dataSet = LineDataSet(data, "").apply { setupStyle() }
            chartView.data = LineData(dataSet).apply { setDrawValues(false) }
        }
        chartView.invalidate()
    }

    private fun setupChartView() {
        with(chartView) {
            setViewPortOffsets(0f, 10f, 0f, 0f)
            description.isEnabled = false
            setTouchEnabled(true)
            setScaleEnabled(false)
            isDragEnabled = true
            setDrawGridBackground(false)

            legend.isEnabled = false
            animateXY(300, 300)

            xAxis.isEnabled = false
            axisLeft.isEnabled = false
            with(axisRight) {
                labelCount = 6
                textColor = R.color.neutrals3.res2color()
                setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART)
                setDrawGridLines(false)
            }
            marker = ChartMarker(activity).apply { chartView = this@with }
            invalidate()
        }
    }

    private fun LineDataSet.setupStyle() {
        mode = LineDataSet.Mode.CUBIC_BEZIER
        cubicIntensity = 0.2f
        setDrawFilled(true)
        setDrawCircles(false)
        lineWidth = 1.8f
        circleRadius = 4f
        color = R.color.salmon_primary.res2color()
        fillDrawable = ContextCompat.getDrawable(activity, R.drawable.bg_line_chart_gradient)
        setDrawHorizontalHighlightIndicator(false)
        fillFormatter = IFillFormatter { _, _ -> chartView.axisLeft.axisMinimum }
    }
}