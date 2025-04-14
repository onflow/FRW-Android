package com.flowfoundation.wallet.page.profile.subpage.currency

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivitySettingsCurrencyBinding
import com.flowfoundation.wallet.page.profile.subpage.currency.model.CurrencyModel
import com.flowfoundation.wallet.page.profile.subpage.currency.presenter.CurrencyPresenter
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.isNightMode

class CurrencyListActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsCurrencyBinding
    private lateinit var presenter: CurrencyPresenter

    private lateinit var viewModel: CurrencyViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsCurrencyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(true).light(!isNightMode(this)).applyNavigationBar()

        setupToolbar()

        presenter = CurrencyPresenter(binding)

        viewModel = ViewModelProvider(this)[CurrencyViewModel::class.java].apply {
            dataLiveData.observe(this@CurrencyListActivity) { presenter.bind(CurrencyModel(data = it)) }
            load()
        }
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
        title = R.string.currency.res2String()
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, CurrencyListActivity::class.java))
        }
    }
}