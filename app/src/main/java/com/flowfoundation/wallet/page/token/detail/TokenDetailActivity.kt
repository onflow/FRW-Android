package com.flowfoundation.wallet.page.token.detail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.flowfoundation.wallet.databinding.ActivityTokenDetailBinding
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.page.token.detail.model.TokenDetailActivitiesModel
import com.flowfoundation.wallet.page.token.detail.model.TokenDetailChartModel
import com.flowfoundation.wallet.page.token.detail.model.TokenDetailModel
import com.flowfoundation.wallet.page.token.detail.presenter.TokenDetailActivitiesPresenter
import com.flowfoundation.wallet.page.token.detail.presenter.TokenDetailChartPresenter
import com.flowfoundation.wallet.page.token.detail.presenter.TokenDetailPresenter
import com.flowfoundation.wallet.utils.isNightMode

class TokenDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityTokenDetailBinding
    private lateinit var presenter: TokenDetailPresenter
    private lateinit var chartPresenter: TokenDetailChartPresenter
    private lateinit var activitiesPresenter: TokenDetailActivitiesPresenter
    private lateinit var viewModel: TokenDetailViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val coin = intent.getParcelableExtra<FlowCoin>(EXTRA_COIN)
        if (coin == null) {
            finish()
            return
        }
        binding = ActivityTokenDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(true).light(!isNightMode(this)).applyNavigationBar()

        presenter = TokenDetailPresenter(this, binding, coin)
        chartPresenter = TokenDetailChartPresenter(this, binding.chartWrapper)
        activitiesPresenter = TokenDetailActivitiesPresenter(this, binding.activitiesWrapper, coin)

        viewModel = ViewModelProvider(this)[TokenDetailViewModel::class.java].apply {
            setCoin(coin)
            balanceAmountLiveData.observe(this@TokenDetailActivity) { presenter.bind(TokenDetailModel(balanceAmount = it)) }
            balancePriceLiveData.observe(this@TokenDetailActivity) { presenter.bind(TokenDetailModel(balancePrice = it)) }
            summaryLiveData.observe(this@TokenDetailActivity) { chartPresenter.bind(TokenDetailChartModel(summary = it)) }
            chartDataLiveData.observe(this@TokenDetailActivity) { chartPresenter.bind(TokenDetailChartModel(chartData = it)) }
            transferListLiveData.observe(this@TokenDetailActivity) { activitiesPresenter.bind(TokenDetailActivitiesModel(recordList = it)) }
            load()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    companion object {
        private const val EXTRA_COIN = "EXTRA_COIN"
        fun launch(context: Context, coin: FlowCoin) {
            context.startActivity(Intent(context, TokenDetailActivity::class.java).apply {
                putExtra(EXTRA_COIN, coin)
            })
        }
    }
}