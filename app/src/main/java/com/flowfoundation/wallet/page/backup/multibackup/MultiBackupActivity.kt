package com.flowfoundation.wallet.page.backup.multibackup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityMultiBackupBinding
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupOption
import com.flowfoundation.wallet.page.backup.multibackup.presenter.MultiBackupPresenter
import com.flowfoundation.wallet.page.backup.multibackup.viewmodel.MultiBackupViewModel


class MultiBackupActivity : BaseActivity() {
    private lateinit var binding: ActivityMultiBackupBinding
    private lateinit var backupPresenter: MultiBackupPresenter
    private lateinit var backupViewModel: MultiBackupViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMultiBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        backupPresenter = MultiBackupPresenter(this)
        backupViewModel = ViewModelProvider(this)[MultiBackupViewModel::class.java].apply {
            optionChangeLiveData.observe(this@MultiBackupActivity) {
                backupPresenter.bind(it)
            }
            changeOption(BackupOption.BACKUP_START, -1)
        }
        setupToolbar()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
            }

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
            context.startActivity(Intent(context, MultiBackupActivity::class.java))
        }

        fun createIntent(context: Context) = Intent(context, MultiBackupActivity::class.java)
    }
}