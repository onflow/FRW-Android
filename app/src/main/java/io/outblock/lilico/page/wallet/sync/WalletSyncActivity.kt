package io.outblock.lilico.page.wallet.sync

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import io.outblock.lilico.R
import io.outblock.lilico.base.activity.BaseActivity
import io.outblock.lilico.databinding.ActivitySyncWalletBinding
import io.outblock.lilico.page.wallet.sync.model.SyncReceiveModel
import io.outblock.lilico.page.wallet.sync.presenter.WalletSyncPresenter
import io.outblock.lilico.utils.isNightMode


class WalletSyncActivity : BaseActivity() {
    private lateinit var binding: ActivitySyncWalletBinding
    private lateinit var presenter: WalletSyncPresenter
    private lateinit var viewModel: WalletSyncViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySyncWalletBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(false).colorRes(R.color.transparent).light(false).applyStatusBar()
        presenter = WalletSyncPresenter(binding)
        viewModel = ViewModelProvider(this)[WalletSyncViewModel::class.java].apply {
            qrCodeLiveData.observe(this@WalletSyncActivity) {
                presenter.bind(
                    SyncReceiveModel(qrCode = it)
                )
            }
            load()
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
        binding.toolbar.navigationIcon?.mutate()?.setTint(Color.WHITE)
        binding.toolbar.addStatusBarTopPadding()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, WalletSyncActivity::class.java))
        }
    }
}