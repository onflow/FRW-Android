package com.flowfoundation.wallet.page.backup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityBackupRecoveryPhraseBinding
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupSeedPhraseOption
import com.flowfoundation.wallet.page.backup.presenter.BackupSeedPhrasePresenter
import com.flowfoundation.wallet.page.backup.viewmodel.BackupSeedPhraseViewModel
import com.flowfoundation.wallet.utils.isNightMode

class BackupRecoveryPhraseActivity : BaseActivity() {

    private lateinit var binding: ActivityBackupRecoveryPhraseBinding
    private lateinit var presenter: BackupSeedPhrasePresenter
    private lateinit var viewModel: BackupSeedPhraseViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupRecoveryPhraseBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(true).colorRes(R.color.background).light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyNavigationBar()
        presenter = BackupSeedPhrasePresenter(this)
        viewModel = ViewModelProvider(this)[BackupSeedPhraseViewModel::class.java].apply {
            optionChangeLiveData.observe(this@BackupRecoveryPhraseActivity) {
                presenter.bind(it)
            }
            changeOption(BackupSeedPhraseOption.BACKUP_WARING)
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
            context.startActivity(Intent(context, BackupRecoveryPhraseActivity::class.java))
        }

        fun createIntent(context: Context) = Intent(context, BackupRecoveryPhraseActivity::class.java)
    }
}