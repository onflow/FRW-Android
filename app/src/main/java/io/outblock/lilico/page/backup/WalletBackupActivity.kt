package io.outblock.lilico.page.backup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import io.outblock.lilico.R
import io.outblock.lilico.base.activity.BaseActivity
import io.outblock.lilico.databinding.ActivityWalletBackupBinding
import io.outblock.lilico.page.backup.multibackup.MultiBackupActivity
import io.outblock.lilico.page.profile.subpage.about.AboutActivity
import io.outblock.lilico.utils.isNightMode


class WalletBackupActivity: BaseActivity() {

    private lateinit var binding: ActivityWalletBackupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWalletBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(true).colorRes(R.color.background).light(!isNightMode(this)).applyStatusBar()

        with(binding) {
            cvCreateDeviceBackup.setOnClickListener {

            }

            cvCreateMultiBackup.setOnClickListener {
                MultiBackupActivity.launch(this@WalletBackupActivity)
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
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, WalletBackupActivity::class.java))
        }
    }
}