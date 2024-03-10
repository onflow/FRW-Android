package com.flowfoundation.wallet.page.staking.amount

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.flowfoundation.wallet.databinding.ActivityStakingAmountBinding
import com.flowfoundation.wallet.manager.staking.StakingProvider
import com.flowfoundation.wallet.page.staking.amount.model.StakingAmountModel
import com.flowfoundation.wallet.page.staking.amount.presenter.StakingAmountPresenter
import com.flowfoundation.wallet.utils.isNightMode

class StakingAmountActivity : BaseActivity() {

    private lateinit var binding: ActivityStakingAmountBinding
    private lateinit var presenter: StakingAmountPresenter
    private lateinit var viewModel: StakingAmountViewModel
    private val provider by lazy { intent.getParcelableExtra<StakingProvider>(EXTRA_PROVIDER)!! }
    private val isUnstake by lazy { intent.getBooleanExtra(EXTRA_UNSTAKE, false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStakingAmountBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(true).light(!isNightMode(this)).applyNavigationBar()

        presenter = StakingAmountPresenter(binding, provider, this, isUnstake)
        viewModel = ViewModelProvider(this)[StakingAmountViewModel::class.java].apply {
            balanceLiveData.observe(this@StakingAmountActivity) { presenter.bind(StakingAmountModel(balance = it)) }
            processingLiveData.observe(this@StakingAmountActivity) { presenter.bind(StakingAmountModel(processingState = it)) }
            load(provider, isUnstake)
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
        private const val EXTRA_PROVIDER = "extra_provider"
        private const val EXTRA_UNSTAKE = "extra_UNSTAKE"
        fun launch(context: Context, provider: StakingProvider, isUnstake: Boolean = false) {
            context.startActivity(Intent(context, StakingAmountActivity::class.java).apply {
                putExtra(EXTRA_PROVIDER, provider)
                putExtra(EXTRA_UNSTAKE, isUnstake)
            })
        }
    }
}