package com.flowfoundation.wallet.page.backup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityBackupViewMnemonicBinding
import com.flowfoundation.wallet.page.backup.presenter.BackupViewMnemonicPresenter
import com.flowfoundation.wallet.page.backup.viewmodel.BackupViewMnemonicViewModel
import com.flowfoundation.wallet.utils.isNightMode
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX

class BackupViewMnemonicActivity: BaseActivity() {
    private lateinit var binding: ActivityBackupViewMnemonicBinding
    private lateinit var presenter: BackupViewMnemonicPresenter
    private lateinit var viewModel: BackupViewMnemonicViewModel
    private val mnemonicData by lazy {
        intent.getStringExtra(EXTRA_MNEMONIC_DATA) ?: ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupViewMnemonicBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(true).colorRes(R.color.background).light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyNavigationBar()
        presenter = BackupViewMnemonicPresenter(this)
        viewModel = ViewModelProvider(this)[BackupViewMnemonicViewModel::class.java].apply {
            viewMnemonicLiveData.observe(this@BackupViewMnemonicActivity) {
                presenter.bind(it)
            }
            toPinCode(mnemonicData)
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
        title = ""
    }

    companion object {
        private const val EXTRA_MNEMONIC_DATA = "extra_mnemonic_data"
        fun launch(context: Context, data: String) {
            context.startActivity(Intent(context, BackupViewMnemonicActivity::class.java).apply {
                putExtra(EXTRA_MNEMONIC_DATA, data)
            })
        }
    }
}