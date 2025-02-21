package com.flowfoundation.wallet.page.landing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityLandingBinding
import com.flowfoundation.wallet.manager.account.OnWalletDataUpdate
import com.flowfoundation.wallet.manager.account.WalletFetcher
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.network.model.WalletListData
import com.flowfoundation.wallet.page.backup.WalletBackupActivity
import com.flowfoundation.wallet.page.landing.utils.AutoScrollViewPager
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.invisible
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.ioScope
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX


class LandingActivity: BaseActivity(), OnWalletDataUpdate {
    private lateinit var binding: ActivityLandingBinding
    private var autoScroll: AutoScrollViewPager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLandingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        UltimateBarX.with(this).fitWindow(false).light(false).applyStatusBar()
        UltimateBarX.with(this).fitWindow(false).light(false).applyNavigationBar()
        WalletFetcher.addListener(this)
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
                tabLayout.visible()
                clButton.gone()
                pbButtonLoading.visible()
                tvTips.visible()
            } else {
                MixpanelManager.accountCreationFinish()
                autoScroll?.stopAutoScroll()
                lottieAnimation.gone()
                tvCreatingWallet.gone()
                tvSecureDesignText.gone()
                tvSecureDesign.gone()
                flLandingDone.visible()
                clButton.visible()
                tabLayout.gone()
                clButton.setBackgroundResource(R.drawable.bg_landing_step_done)
                clButton.setOnClickListener {
                    WalletBackupActivity.launch(this@LandingActivity, fromRegistration = true)
                    finish()
                }
                tvButtonTitle.setText(R.string.landing_step_done)
                tvButtonTitle.setTextColor(R.color.white_90.res2color())
                pbButtonLoading.gone()
                tvTips.invisible()
            }
        }
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, LandingActivity::class.java))
            (context as? Activity)?.overridePendingTransition(0, 0)
        }
    }

}