package com.flowfoundation.wallet.page.staking.providers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityStakeProviderBinding
import com.flowfoundation.wallet.page.staking.providers.presenter.StakingProviderPresenter

class StakingProviderActivity : BaseActivity() {

    private lateinit var binding: ActivityStakeProviderBinding
    private lateinit var presenter: StakingProviderPresenter
    private lateinit var viewModel: StakeProviderViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStakeProviderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        presenter = StakingProviderPresenter(binding)
        viewModel = ViewModelProvider(this)[StakeProviderViewModel::class.java].apply {
            data.observe(this@StakingProviderActivity) { presenter.bind(it) }
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
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, StakingProviderActivity::class.java))
        }
    }
}