package com.flowfoundation.wallet.page.profile.subpage.developer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityDeveloperModeSettingBinding
import com.flowfoundation.wallet.manager.app.chainNetwork
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.clearUserCache
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.page.profile.subpage.developer.model.DeveloperPageModel
import com.flowfoundation.wallet.page.profile.subpage.developer.presenter.DeveloperModePresenter
import com.flowfoundation.wallet.utils.isNightMode
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope

class DeveloperModeActivity : BaseActivity() {
    private lateinit var binding: ActivityDeveloperModeSettingBinding
    private lateinit var presenter: DeveloperModePresenter
    private lateinit var viewModel: DeveloperModeViewModel

    private val initNetWork = chainNetwork()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeveloperModeSettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        UltimateBarX.with(this).fitWindow(true).colorRes(R.color.background).light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyNavigationBar()
        setupToolbar()

        presenter = DeveloperModePresenter(this, binding)
        viewModel = ViewModelProvider(this)[DeveloperModeViewModel::class.java].apply {
            progressVisibleLiveData.observe(this@DeveloperModeActivity) { presenter.bind(DeveloperPageModel(progressDialogVisible = it)) }
            resultLiveData.observe(this@DeveloperModeActivity) { presenter.bind(DeveloperPageModel(result = it)) }
        }
        logd(TAG, "initNetWork:$initNetWork")
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onBackPressed() {
        finish()
        super.onBackPressed()
    }

    override fun finish() {
        super.finish()
        if (initNetWork != chainNetwork()) {
            uiScope {
                WalletManager.changeNetwork()
                clearUserCache()
                MainActivity.relaunch(this)
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    companion object {
        private val TAG = DeveloperModeActivity::class.java.simpleName
        fun launch(context: Context) {
            context.startActivity(Intent(context, DeveloperModeActivity::class.java))
        }
    }
}