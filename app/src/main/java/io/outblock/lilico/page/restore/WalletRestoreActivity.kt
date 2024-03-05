package io.outblock.lilico.page.restore

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.MenuItem
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import io.outblock.lilico.R
import io.outblock.lilico.base.activity.BaseActivity
import io.outblock.lilico.databinding.ActivityWalletRestoreBinding
import io.outblock.lilico.manager.app.isTestnet
import io.outblock.lilico.page.restore.multirestore.MultiRestoreActivity
import io.outblock.lilico.page.wallet.sync.WalletSyncActivity
import io.outblock.lilico.utils.extensions.res2String
import io.outblock.lilico.utils.extensions.res2color
import io.outblock.lilico.utils.isNightMode
import io.outblock.lilico.widgets.DialogType
import io.outblock.lilico.widgets.SwitchNetworkDialog


class WalletRestoreActivity : BaseActivity() {

    private lateinit var binding: ActivityWalletRestoreBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWalletRestoreBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(true).colorRes(R.color.background).light(!isNightMode(this)).applyStatusBar()

        with(binding) {
            tvTitle.text = SpannableString(R.string.import_wallet.res2String()).apply {
                setSpan(ForegroundColorSpan(R.color.accent_green.res2color()), 0, 6, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            llImportFromDevice.setOnClickListener {
                WalletSyncActivity.launch(this@WalletRestoreActivity)
            }

            llImportFromBackup.setOnClickListener {
                if (isTestnet()) {
                    SwitchNetworkDialog(this@WalletRestoreActivity, DialogType.RESTORE).show()
                } else {
                    MultiRestoreActivity.launch(this@WalletRestoreActivity)
                }
            }

            llImportFromRecoveryPhrase.setOnClickListener {
                io.outblock.lilico.page.walletrestore.WalletRestoreActivity.launch(this@WalletRestoreActivity)
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
            context.startActivity(Intent(context, WalletRestoreActivity::class.java))
        }
    }
}