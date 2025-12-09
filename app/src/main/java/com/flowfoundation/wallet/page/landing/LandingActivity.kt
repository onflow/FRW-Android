package com.flowfoundation.wallet.page.landing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityLandingBinding
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.OnAccountUpdate
import com.flowfoundation.wallet.manager.walletdata.WalletDataManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.page.backup.WalletBackupActivity
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.ioScope
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX

class LandingActivity: BaseActivity(), OnAccountUpdate {
    private  lateinit var binding: ActivityLandingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLandingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        UltimateBarX.with(this).fitWindow(false).light(false).applyStatusBar()
        UltimateBarX.with(this).fitWindow(false).light(false).applyNavigationBar()
        AccountManager.addListener(this)
        checkWalletInfo()
    }

    override fun onAccountUpdate(account: Account) {
        setupButton(false)
    }

    private fun checkWalletInfo() {
        setupButton()
        ioScope {
            WalletDataManager.updateCurrentAccount()
        }
    }

    private fun setupButton(isLoading: Boolean = true) {
        with(binding) {
            if (isLoading) {
                clLandingDone.gone()
                clButton.gone()
                tvTips.visible()
            } else {
                MixpanelManager.accountCreationFinish()
                lottieAnimation.gone()
                tvCreatingWallet.gone()
                tvSecureDesignText.gone()
                tvSecureDesign.gone()
                clLandingDone.visible()
                clButton.visible()
                clButton.setOnClickListener {
                    WalletBackupActivity.launch(this@LandingActivity, fromRegistration = true)
                    finish()
                }
                tvTips.gone()
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
