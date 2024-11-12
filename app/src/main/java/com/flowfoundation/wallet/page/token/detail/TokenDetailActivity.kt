package com.flowfoundation.wallet.page.token.detail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.crowdin.platform.util.inflateWithCrowdin
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.flowfoundation.wallet.databinding.ActivityTokenDetailBinding
import com.flowfoundation.wallet.manager.coin.CustomTokenManager
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.page.token.detail.model.TokenDetailActivitiesModel
import com.flowfoundation.wallet.page.token.detail.model.TokenDetailChartModel
import com.flowfoundation.wallet.page.token.detail.model.TokenDetailModel
import com.flowfoundation.wallet.page.token.detail.presenter.TokenDetailActivitiesPresenter
import com.flowfoundation.wallet.page.token.detail.presenter.TokenDetailChartPresenter
import com.flowfoundation.wallet.page.token.detail.presenter.TokenDetailPresenter
import com.flowfoundation.wallet.page.token.detail.widget.TokenDetailPopupMenu
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.isNightMode

class TokenDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityTokenDetailBinding
    private lateinit var presenter: TokenDetailPresenter
    private lateinit var chartPresenter: TokenDetailChartPresenter
    private lateinit var activitiesPresenter: TokenDetailActivitiesPresenter
    private lateinit var viewModel: TokenDetailViewModel
    private val coin by lazy {
        intent.getParcelableExtra<FlowCoin>(EXTRA_COIN)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        coin?.let { coin ->
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
        } ?: run {
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val isCustomToken = CustomTokenManager.isCustomToken(coin?.address.orEmpty())
        menuInflater.inflateWithCrowdin(R.menu.token_detail_more, menu, resources)
        val menuItem = menu.findItem(R.id.action_more)
        menuItem.setVisible(isCustomToken)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.action_more -> showMoreAction()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun showMoreAction() {
        coin?.let {
            TokenDetailPopupMenu(binding.space, it).show()
        }
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