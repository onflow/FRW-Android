package com.flowfoundation.wallet.page.swap

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.flowfoundation.wallet.databinding.ActivitySwapBinding
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.page.swap.model.SwapModel
import com.flowfoundation.wallet.page.swap.presenter.SwapPresenter
import com.flowfoundation.wallet.utils.isNightMode

class SwapActivity : BaseActivity() {

    private lateinit var binding: ActivitySwapBinding
    private lateinit var viewModel: SwapViewModel
    private lateinit var presenter: SwapPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySwapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(true).light(!isNightMode(this)).applyNavigationBar()

        presenter = SwapPresenter(binding, this)
        viewModel = ViewModelProvider(this)[SwapViewModel::class.java].apply {
            fromCoinLiveData.observe(this@SwapActivity) { presenter.bind(SwapModel(fromCoin = it)) }
            toCoinLiveData.observe(this@SwapActivity) { presenter.bind(SwapModel(toCoin = it)) }
            onBalanceUpdate.observe(this@SwapActivity) { presenter.bind(SwapModel(onBalanceUpdate = it)) }
            onCoinRateUpdate.observe(this@SwapActivity) { presenter.bind(SwapModel(onCoinRateUpdate = it)) }
            onEstimateFromUpdate.observe(this@SwapActivity) { presenter.bind(SwapModel(onEstimateFromUpdate = it)) }
            onEstimateToUpdate.observe(this@SwapActivity) { presenter.bind(SwapModel(onEstimateToUpdate = it)) }
            onEstimateLoading.observe(this@SwapActivity) { presenter.bind(SwapModel(onEstimateLoading = it)) }
            estimateLiveData.observe(this@SwapActivity) { presenter.bind(SwapModel(estimateData = it)) }
            initFromCoin(intent.getStringExtra(EXTRA_COIN_CONTRACT_ID) ?: FlowCoinListManager.getFlowCoinContractId())
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
        const val EXTRA_COIN_CONTRACT_ID = "extra_coin_contract_id"
        fun launch(context: Context, contractId: String? = FlowCoinListManager.getFlowCoinContractId()) {
            val intent = Intent(context, SwapActivity::class.java)
            intent.putExtra(EXTRA_COIN_CONTRACT_ID, contractId)
            context.startActivity(intent)
        }
    }
}