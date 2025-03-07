package com.flowfoundation.wallet.page.security

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivitySecuritySettingBinding
import com.flowfoundation.wallet.manager.biometric.BlockBiometricManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.mixpanel.MixpanelSecurityTool
import com.flowfoundation.wallet.page.security.pin.SecurityPinActivity
import com.flowfoundation.wallet.utils.*
import com.flowfoundation.wallet.utils.extensions.res2String

class SecuritySettingActivity : BaseActivity() {
    private lateinit var binding: ActivitySecuritySettingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecuritySettingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(false).colorRes(R.color.background).light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyNavigationBar()

        binding.root.addStatusBarTopPadding()
        setupToolbar()
        setup()
    }

    private fun setup() {
        with(binding) {
            pinPreference.setOnClickListener {
                SecurityPinActivity.launch(
                    this@SecuritySettingActivity,
                    if (getPinCode().isBlank()) SecurityPinActivity.TYPE_CREATE else SecurityPinActivity.TYPE_RESET
                )
            }
            biometricsPreference.setOnClickListener { toggleBiometricsChecked() }

            uiScope { biometricsPreference.setChecked(isBiometricEnable()) }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        title = R.string.security.res2String()
    }

    private fun ActivitySecuritySettingBinding.toggleBiometricsChecked() {
        if (biometricsPreference.isChecked()) {
            biometricsPreference.setChecked(false)
            setBiometricEnable(false)
            MixpanelManager.securityTool(MixpanelSecurityTool.NONE)
        } else {
            BlockBiometricManager.showBiometricPrompt(this@SecuritySettingActivity) { isSuccess, errorMsg ->
                uiScope { biometricsPreference.setChecked(isSuccess) }
                if (isSuccess) {
                    setBiometricEnable(true)
                    MixpanelManager.securityTool(MixpanelSecurityTool.BIOMETRIC)
                } else {
                    setBiometricEnable(false)
                    MixpanelManager.securityTool(MixpanelSecurityTool.NONE)
                    toast(msg = errorMsg)
                }
            }
        }
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, SecuritySettingActivity::class.java))
        }
    }
}