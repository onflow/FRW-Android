package com.flowfoundation.wallet.page.profile.subpage.wallet

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.cache.storageInfoCache
import com.flowfoundation.wallet.databinding.ActivityWalletSettingBinding
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.page.profile.subpage.claimdomain.ClaimDomainActivity
import com.flowfoundation.wallet.page.profile.subpage.claimdomain.checkMeowDomainClaimed
import com.flowfoundation.wallet.page.profile.subpage.wallet.dialog.WalletResetConfirmDialog
import com.flowfoundation.wallet.page.profile.subpage.wallet.key.AccountKeyActivity
import com.flowfoundation.wallet.page.security.recovery.SecurityPrivateKeyActivity
import com.flowfoundation.wallet.page.security.recovery.SecurityRecoveryActivity
import com.flowfoundation.wallet.page.security.securityOpen
import com.flowfoundation.wallet.utils.*
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.visible

class WalletSettingActivity : BaseActivity() {

    private lateinit var binding: ActivityWalletSettingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWalletSettingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(false).colorRes(R.color.background).light(!isNightMode(this)).applyStatusBar()
        binding.root.addStatusBarTopPadding()
        setupToolbar()
        setup()
        checkMeowDomainClaimed()
        queryStorageInfo()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    @SuppressLint("SetTextI18n")
    private fun setup() {
        with(binding) {
            if (CryptoProviderManager.isHDWalletCrypto()) {
                group1.visible()
                privatePreference.setOnClickListener {
                    securityOpen(SecurityPrivateKeyActivity.launchIntent(this@WalletSettingActivity))
                }
                recoveryPreference.setOnClickListener {
                    securityOpen(SecurityRecoveryActivity.launchIntent(this@WalletSettingActivity))
                }
            } else {
                group1.gone()
            }

            accountKey.setOnClickListener {
                securityOpen(AccountKeyActivity.launchIntent(this@WalletSettingActivity))
            }

            uiScope { freeGasPreference.setChecked(isFreeGasPreferenceEnable()) }
            freeGasPreference.setOnCheckedChangeListener { uiScope { setFreeGasPreferenceEnable(it) } }

            resetButton.setOnClickListener { WalletResetConfirmDialog.show(supportFragmentManager) }

            claimButton.setOnClickListener { ClaimDomainActivity.launch(this@WalletSettingActivity) }

            uiScope {
                claimDomainWrapper.gone()
//                todo hide domain entrance for rebranding
//                claimDomainWrapper.setVisible(!isMeowDomainClaimed())
            }

            ioScope {
                val storageInfo = storageInfoCache().read() ?: return@ioScope
                uiScope {
                    group4.setVisible(true)
                    val progress = storageInfo.used.toFloat() / storageInfo.capacity
                    storageInfoUsed.text = (progress * 100).formatNum(3) + "%"
                    storageInfoCount.text = getString(
                        R.string.storage_info_count,
                        toHumanReadableSIPrefixes(storageInfo.used),
                        toHumanReadableSIPrefixes(storageInfo.capacity)
                    )
                    storageInfoProgress.progress = (progress * 1000).toInt()
                }
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        title = R.string.wallet.res2String()
    }

    companion object {

        fun launch(context: Context) {
            context.startActivity(Intent(context, WalletSettingActivity::class.java))
        }
    }
}