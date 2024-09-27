package com.flowfoundation.wallet.page.restore

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityRawKeyRestoreBinding
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.page.restore.keystore.KeyStoreRestoreActivity
import com.flowfoundation.wallet.utils.isNightMode
import com.flowfoundation.wallet.widgets.DialogType
import com.flowfoundation.wallet.widgets.SwitchNetworkDialog
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX


class RawKeyRestoreActivity: BaseActivity() {

    private lateinit var binding: ActivityRawKeyRestoreBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRawKeyRestoreBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(true).colorRes(R.color.bg_2).light(!isNightMode(this)).applyStatusBar()
        with(binding) {

            clImportFromGoogleDrive.setOnClickListener {
                if (isTestnet()) {
                    SwitchNetworkDialog(this@RawKeyRestoreActivity, DialogType.RESTORE).show()
                } else {
                    com.flowfoundation.wallet.page.walletrestore.WalletRestoreActivity.launch(this@RawKeyRestoreActivity)
                }
            }
            clImportFromKeyStore.setOnClickListener {
                if (isTestnet()) {
                    SwitchNetworkDialog(this@RawKeyRestoreActivity, DialogType.RESTORE).show()
                } else {
                    KeyStoreRestoreActivity.launchKeyStore(this@RawKeyRestoreActivity)
                }
            }

            clImportFromSeedPhrase.setOnClickListener {
                if (isTestnet()) {
                    SwitchNetworkDialog(this@RawKeyRestoreActivity, DialogType.RESTORE).show()
                } else {
                    KeyStoreRestoreActivity.launchSeedPhrase(this@RawKeyRestoreActivity)
                }
            }

            clImportFromPrivateKey.setOnClickListener {
                if (isTestnet()) {
                    SwitchNetworkDialog(this@RawKeyRestoreActivity, DialogType.RESTORE).show()
                } else {
                    KeyStoreRestoreActivity.launchPrivateKey(this@RawKeyRestoreActivity)
                }
            }

        }
        setupToolbar()
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
        title = ""
    }


    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, RawKeyRestoreActivity::class.java))
        }
    }
}