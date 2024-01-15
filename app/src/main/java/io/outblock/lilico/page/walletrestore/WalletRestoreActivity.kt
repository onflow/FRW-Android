package io.outblock.lilico.page.walletrestore

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import io.outblock.lilico.base.activity.BaseActivity
import io.outblock.lilico.databinding.ActivityRestoreWalletBinding
import io.outblock.lilico.page.walletrestore.model.WalletRestoreContentModel
import io.outblock.lilico.page.walletrestore.presenter.WalletRestoreContentPresenter

class WalletRestoreActivity : BaseActivity() {

    private lateinit var binding: ActivityRestoreWalletBinding
    private lateinit var contentPresenter: WalletRestoreContentPresenter
    private lateinit var viewModel: WalletRestoreViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRestoreWalletBinding.inflate(layoutInflater)
        setContentView(binding.root)

        contentPresenter = WalletRestoreContentPresenter(this, binding)

        viewModel = ViewModelProvider(this)[WalletRestoreViewModel::class.java].apply {
            onStepChangeLiveData.observe(this@WalletRestoreActivity) {
                contentPresenter.bind(
                    WalletRestoreContentModel(changeStep = it)
                )
            }
            changeStep(WALLET_RESTORE_STEP_MNEMONIC)
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
        title = ""
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, WalletRestoreActivity::class.java))
        }
    }
}