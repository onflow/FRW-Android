package com.flowfoundation.wallet.page.staking.list

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityStakeListBinding
import com.flowfoundation.wallet.page.staking.list.presenter.StakingListPresenter

class StakingListActivity : BaseActivity() {

    private lateinit var binding: ActivityStakeListBinding
    private lateinit var presenter: StakingListPresenter
    private lateinit var viewModel: StakeListViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStakeListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        presenter = StakingListPresenter(binding)
        viewModel = ViewModelProvider(this)[StakeListViewModel::class.java].apply {
            data.observe(this@StakingListActivity) { presenter.bind(it) }
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
            context.startActivity(Intent(context, StakingListActivity::class.java))
        }
    }
}