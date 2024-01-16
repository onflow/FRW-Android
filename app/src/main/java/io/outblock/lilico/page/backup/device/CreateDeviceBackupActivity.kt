package io.outblock.lilico.page.backup.device

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModelProvider
import com.journeyapps.barcodescanner.ScanOptions
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import io.outblock.lilico.R
import io.outblock.lilico.base.activity.BaseActivity
import io.outblock.lilico.databinding.ActivityCreateDeviceBackupBinding
import io.outblock.lilico.page.scan.dispatchScanResult
import io.outblock.lilico.page.wallet.sync.WalletSyncViewModel
import io.outblock.lilico.utils.isNightMode
import io.outblock.lilico.utils.launch
import io.outblock.lilico.utils.registerBarcodeLauncher


class CreateDeviceBackupActivity: BaseActivity() {

    private lateinit var binding: ActivityCreateDeviceBackupBinding

    private lateinit var barcodeLauncher: ActivityResultLauncher<ScanOptions>
    private lateinit var viewModel: WalletSyncViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        barcodeLauncher = registerBarcodeLauncher { result -> dispatchScanResult(this, result.orEmpty()) }
        binding = ActivityCreateDeviceBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(true).colorRes(R.color.background).light(!isNightMode(this)).applyStatusBar()

        viewModel = ViewModelProvider(this)[WalletSyncViewModel::class.java].apply {
            qrCodeLiveData.observe(this@CreateDeviceBackupActivity) {
                binding.ivQrCode.setImageBitmap(it)
            }
            load()
        }

        binding.llScan.setOnClickListener {
            barcodeLauncher.launch()
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
            context.startActivity(Intent(context, CreateDeviceBackupActivity::class.java))
        }
    }
}