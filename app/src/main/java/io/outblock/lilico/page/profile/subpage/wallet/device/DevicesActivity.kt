package io.outblock.lilico.page.profile.subpage.wallet.device

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModelProvider
import com.journeyapps.barcodescanner.ScanOptions
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import io.outblock.lilico.R
import io.outblock.lilico.base.activity.BaseActivity
import io.outblock.lilico.databinding.ActivityDevicesBinding
import io.outblock.lilico.page.profile.subpage.wallet.device.presenter.DevicesPresenter
import io.outblock.lilico.page.scan.dispatchScanResult
import io.outblock.lilico.utils.extensions.res2String
import io.outblock.lilico.utils.isNightMode
import io.outblock.lilico.utils.launch
import io.outblock.lilico.utils.registerBarcodeLauncher


class DevicesActivity : BaseActivity() {
    private lateinit var binding: ActivityDevicesBinding
    private lateinit var viewModel: DevicesViewModel
    private lateinit var presenter: DevicesPresenter
    private lateinit var barcodeLauncher: ActivityResultLauncher<ScanOptions>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        barcodeLauncher = registerBarcodeLauncher { result -> dispatchScanResult(this, result.orEmpty()) }
        binding = ActivityDevicesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(false).colorRes(R.color.background)
            .light(!isNightMode(this)).applyStatusBar()
        binding.root.addStatusBarTopPadding()

        presenter = DevicesPresenter(binding)
        viewModel = ViewModelProvider(this)[DevicesViewModel::class.java].apply {
            devicesLiveData.observe(this@DevicesActivity) {
                presenter.bind(it)
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
        title = R.string.devices.res2String()
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(launchIntent(context))
        }

        fun launchIntent(context: Context): Intent = Intent(context, DevicesActivity::class.java)
    }
}