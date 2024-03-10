package com.flowfoundation.wallet.page.wallet.sync

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivitySyncWalletBinding
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.toast
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding


class WalletSyncActivity : BaseActivity() {
    private lateinit var binding: ActivitySyncWalletBinding
    private val viewModel by lazy { ViewModelProvider(this)[WalletSyncViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySyncWalletBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(false).colorRes(R.color.transparent).light(false).applyStatusBar()
        viewModel.apply {
            qrCodeLiveData.observe(this@WalletSyncActivity) {
                binding.progressBar.gone()
                if (it == null) {
                    toast(msgRes = R.string.generate_qr_code_error)
                    binding.ivRetry.visible()
                    return@observe
                }
                binding.ivQrCode.setImageBitmap(it)
            }
            generateQRCode()
        }
        binding.ivRetry.setOnClickListener {
            binding.ivRetry.gone()
            generateQRCode()
        }
        setupToolbar()
    }

    private fun generateQRCode() {
        binding.progressBar.visible()
        viewModel.load()
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