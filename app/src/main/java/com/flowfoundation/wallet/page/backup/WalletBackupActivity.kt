package com.flowfoundation.wallet.page.backup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityWalletBackupBinding
import com.flowfoundation.wallet.page.backup.presenter.WalletBackupPresenter
import com.flowfoundation.wallet.page.backup.viewmodel.WalletBackupViewModel
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.utils.isNightMode


class WalletBackupActivity: BaseActivity() {

    private lateinit var binding: ActivityWalletBackupBinding
    private lateinit var viewModel: WalletBackupViewModel
    private lateinit var presenter: WalletBackupPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWalletBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(true).colorRes(R.color.background).light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(true).light(!isNightMode(this)).applyNavigationBar()

        presenter = WalletBackupPresenter(this, binding)
        viewModel = ViewModelProvider(this)[WalletBackupViewModel::class.java].apply {
            backupListLiveData.observe(this@WalletBackupActivity) {
                presenter.bindBackupList(it)
            }
            devicesLiveData.observe(this@WalletBackupActivity) {
                presenter.bindDeviceList(it)
            }
            seedPhraseListLiveData.observe(this@WalletBackupActivity) {
                presenter.bindSeedPhraseList(it)
            }

        }
        setupToolbar()
    }

    private val fromRegistration: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_FROM_REGISTRATION, false)
    }


    override fun onResume() {
        super.onResume()
        viewModel.loadData()
        presenter.showLoading()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                if (fromRegistration) {
                    MainActivity.launch(this)
                }
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }


    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    companion object {
        private const val EXTRA_FROM_REGISTRATION = "EXTRA_FROM_REGISTRATION"

        fun launch(context: Context, fromRegistration: Boolean = false) {
            val intent = Intent(context, WalletBackupActivity::class.java).apply {
                putExtra(EXTRA_FROM_REGISTRATION, fromRegistration)
            }
            context.startActivity(intent)
        }
    }
}