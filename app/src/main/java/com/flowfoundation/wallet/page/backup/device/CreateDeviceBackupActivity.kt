package com.flowfoundation.wallet.page.backup.device

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModelProvider
import com.journeyapps.barcodescanner.ScanOptions
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityCreateDeviceBackupBinding
import com.flowfoundation.wallet.page.scan.dispatchScanResult
import com.flowfoundation.wallet.page.wallet.sync.WalletSyncViewModel
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.isNightMode
import com.flowfoundation.wallet.utils.launch
import com.flowfoundation.wallet.utils.registerBarcodeLauncher
import com.flowfoundation.wallet.utils.toast


class CreateDeviceBackupActivity: BaseActivity() {

    private lateinit var binding: ActivityCreateDeviceBackupBinding

    private lateinit var barcodeLauncher: ActivityResultLauncher<ScanOptions>
    private val viewModel by lazy { ViewModelProvider(this)[WalletSyncViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        barcodeLauncher = registerBarcodeLauncher { result -> dispatchScanResult(this, result.orEmpty()) }
        binding = ActivityCreateDeviceBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(true).colorRes(R.color.background).light(!isNightMode(this)).applyStatusBar()

        viewModel.apply {
            qrCodeLiveData.observe(this@CreateDeviceBackupActivity) {
                binding.progressBar.gone()
                if (it == null) {
                    toast(msgRes = R.string.generate_qr_code_error)
                    binding.ivRetry.visible()
                    return@observe
                }
                binding.ivQrCode.setImageDrawable(it)
            }
            generateQRCode()
        }

        binding.llScan.setOnClickListener {
            barcodeLauncher.launch()
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
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, CreateDeviceBackupActivity::class.java))
        }
    }
}