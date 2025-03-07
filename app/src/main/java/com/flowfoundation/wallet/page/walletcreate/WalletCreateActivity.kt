package com.flowfoundation.wallet.page.walletcreate

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityCreateWalletBinding
import com.flowfoundation.wallet.page.walletcreate.model.WalletCreateContentModel
import com.flowfoundation.wallet.page.walletcreate.presenter.WalletCreateContentPresenter

class WalletCreateActivity : BaseActivity() {

    private lateinit var binding: ActivityCreateWalletBinding
    private lateinit var contentPresenter: WalletCreateContentPresenter
    private lateinit var viewModel: WalletCreateViewModel

    private val step by lazy { intent.getIntExtra(STEP, WALLET_CREATE_STEP_USERNAME) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateWalletBinding.inflate(layoutInflater)
        setContentView(binding.root)

        contentPresenter = WalletCreateContentPresenter(this)

        viewModel = ViewModelProvider(this)[WalletCreateViewModel::class.java].apply {
            onStepChangeLiveData.observe(this@WalletCreateActivity) {
                contentPresenter.bind(WalletCreateContentModel(changeStep = it))
            }

            changeStep(step)
        }

        setupToolbar()
    }

    override fun onBackPressed() {
        if (viewModel.handleBackPressed()) {
            return
        }
        super.onBackPressed()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                if (viewModel.handleBackPressed()) {
                    return true
                }
                finish()
            }

            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        title = ""
    }

    companion object {
        private const val STEP = "extra_step"

        fun launch(context: Context, step: Int = WALLET_CREATE_STEP_USERNAME) {
            context.startActivity(Intent(context, WalletCreateActivity::class.java).apply {
                putExtra(STEP, step)
            })
        }
    }
}