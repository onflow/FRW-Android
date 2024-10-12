package com.flowfoundation.wallet.page.staking.detail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.flowfoundation.wallet.databinding.ActivityStakingDetailBinding
import com.flowfoundation.wallet.manager.staking.StakingInfoUpdateListener
import com.flowfoundation.wallet.manager.staking.StakingManager
import com.flowfoundation.wallet.manager.staking.StakingProvider
import com.flowfoundation.wallet.page.staking.detail.presenter.StakingDetailPresenter
import com.flowfoundation.wallet.utils.isNightMode

class StakingDetailActivity : BaseActivity(), StakingInfoUpdateListener {

    private lateinit var binding: ActivityStakingDetailBinding
    private lateinit var presenter: StakingDetailPresenter
    private lateinit var viewModel: StakingDetailViewModel
    private val provider by lazy { intent.getParcelableExtra<StakingProvider>(EXTRA_PROVIDER)!! }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStakingDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(true).light(!isNightMode(this)).applyNavigationBar()

        StakingManager.addStakingInfoUpdateListener(this)
        presenter = StakingDetailPresenter(binding, provider, this)
        viewModel = ViewModelProvider(this)[StakingDetailViewModel::class.java].apply {
            load(provider)
            dataLiveData.observe(this@StakingDetailActivity) { presenter.bind(it) }
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
        fun launch(context: Context, provider: StakingProvider) {
            context.startActivity(Intent(context, StakingDetailActivity::class.java).apply {
                putExtra(EXTRA_PROVIDER, provider)
            })
        }
    }

    override fun onStakingInfoUpdate() {
        viewModel.load(provider)
    }
}