package com.flowfoundation.wallet.page.landing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityLandingBinding
import com.flowfoundation.wallet.manager.account.OnWalletDataUpdate
import com.flowfoundation.wallet.manager.account.WalletFetcher
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.network.model.WalletListData
import com.flowfoundation.wallet.page.landing.adapter.LandingItemAdapter
import com.flowfoundation.wallet.page.landing.model.LandingItemModel
import com.flowfoundation.wallet.page.landing.utils.AutoScrollViewPager
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.invisible
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.ioScope
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX


class LandingActivity: BaseActivity(), OnWalletDataUpdate {
    private lateinit var binding: ActivityLandingBinding
    private val adapter by lazy { LandingItemAdapter() }
    private var autoScroll: AutoScrollViewPager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLandingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        UltimateBarX.with(this).fitWindow(false).light(false).applyStatusBar()
        UltimateBarX.with(this).fitWindow(false).light(false).applyNavigationBar()
        WalletFetcher.addListener(this)
        setupData()
        setupViewPager()
        checkWalletInfo()
    }

    private fun checkWalletInfo() {
        setupButton()
        ioScope {
            WalletFetcher.fetch()
        }
    }

    override fun onWalletDataUpdate(wallet: WalletListData) {
        setupButton(false)
    }

    private fun setupButton(isLoading: Boolean = true) {
        with(binding) {
            if (isLoading) {
                flLandingDone.gone()
                viewPager.visible()
                tabLayout.visible()
                clButton.setBackgroundResource(R.drawable.bg_landing_step_loading)
                tvButtonTitle.setText(R.string.landing_step_loading)
                tvButtonTitle.setTextColor(R.color.accent_green.res2color())
                pbButtonLoading.visible()
                tvTips.visible()
            } else {
                MixpanelManager.accountCreationFinish()
                autoScroll?.stopAutoScroll()
                flLandingDone.visible()
                viewPager.invisible()
                tabLayout.gone()
                clButton.setBackgroundResource(R.drawable.bg_landing_step_done)
                clButton.setOnClickListener {
                    MainActivity.launch(this@LandingActivity)
                    finish()
                }
                tvButtonTitle.setText(R.string.landing_step_done)
                tvButtonTitle.setTextColor(R.color.white_90.res2color())
                pbButtonLoading.gone()
                tvTips.invisible()
            }
        }
    }

    private fun setupData() {
        adapter.setNewDiffData(
            listOf(
                LandingItemModel(R.drawable.ic_landing_step_one, R.string.landing_step_one_title,
                    R.string.landing_step_one_desc),
                LandingItemModel(R.drawable.ic_landing_step_two, R.string.landing_step_two_title,
                    R.string.landing_step_two_desc),
                LandingItemModel(R.drawable.ic_landing_step_three, R.string.landing_step_three_title,
                    R.string.landing_step_three_desc),
                LandingItemModel(R.drawable.ic_landing_step_one, R.string.landing_step_one_title,
                    R.string.landing_step_one_desc),
            )
        )
    }

    private fun setupViewPager() {
        binding.tabLayout.setMaxCount(3)
        with(binding.viewPager) {
            adapter = this@LandingActivity.adapter
            (getChildAt(0) as RecyclerView).overScrollMode = View.OVER_SCROLL_NEVER
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {

                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    binding.tabLayout.onTabSelected(position)
                }

                override fun onPageScrollStateChanged(state: Int) {
                    if (state == ViewPager2.SCROLL_STATE_IDLE && currentItem == (adapter as
                                LandingItemAdapter).itemCount - 1) {
                        setCurrentItem(0, false)
                    }
                }
            })
            autoScroll = AutoScrollViewPager(this, 10000, lifecycle)
        }
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, LandingActivity::class.java))
            (context as? Activity)?.overridePendingTransition(0, 0)
        }
    }

}