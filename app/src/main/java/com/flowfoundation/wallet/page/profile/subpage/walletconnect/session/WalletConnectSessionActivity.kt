package com.flowfoundation.wallet.page.profile.subpage.walletconnect.session

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.crowdin.platform.util.inflateWithCrowdin
import com.journeyapps.barcodescanner.ScanOptions
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityWalletConnectSessionBinding
import com.flowfoundation.wallet.page.profile.subpage.walletconnect.session.adapter.WalletConnectSessionsAdapter
import com.flowfoundation.wallet.page.scan.dispatchScanResult
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.isNightMode
import com.flowfoundation.wallet.utils.launch
import com.flowfoundation.wallet.utils.registerBarcodeLauncher
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration
import com.flowfoundation.wallet.widgets.setColorTint

class WalletConnectSessionActivity : BaseActivity() {

    private lateinit var binding: ActivityWalletConnectSessionBinding
    private lateinit var viewModel: WalletConnectSessionViewModel

    private val adapter = WalletConnectSessionsAdapter()

    private lateinit var barcodeLauncher: ActivityResultLauncher<ScanOptions>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        barcodeLauncher = registerBarcodeLauncher { result -> dispatchScanResult(this, result.orEmpty()) }

        binding = ActivityWalletConnectSessionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(true).colorRes(R.color.background).light(!isNightMode(this)).applyStatusBar()

        viewModel = ViewModelProvider(this)[WalletConnectSessionViewModel::class.java].apply {
            dataListLiveData.observe(this@WalletConnectSessionActivity) {
                binding.emptyWrapper.setVisible(it.isEmpty())
                adapter.setNewDiffData(it)
            }
        }

        with(binding.recyclerView) {
            layoutManager = LinearLayoutManager(this@WalletConnectSessionActivity)
            addItemDecoration(ColorDividerItemDecoration(Color.TRANSPARENT, 8.dp2px().toInt()))
            adapter = this@WalletConnectSessionActivity.adapter
        }

        binding.scanLottieView.setColorTint(R.color.wallet_connect.res2color())
        binding.connectButton.setOnClickListener { barcodeLauncher.launch() }
        setupToolbar()
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflateWithCrowdin(R.menu.qr_scan, menu, resources)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.action_scan -> barcodeLauncher.launch()
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
            context.startActivity(Intent(context, WalletConnectSessionActivity::class.java))
        }
    }
}