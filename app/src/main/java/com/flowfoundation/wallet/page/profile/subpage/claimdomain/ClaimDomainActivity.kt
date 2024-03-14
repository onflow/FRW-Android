package com.flowfoundation.wallet.page.profile.subpage.claimdomain

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.zackratos.ultimatebarx.ultimatebarx.addNavigationBarBottomPadding
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityClaimDomainBinding
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.isNightMode
import com.flowfoundation.wallet.utils.toast

class ClaimDomainActivity : BaseActivity() {

    private lateinit var binding: ActivityClaimDomainBinding
    private lateinit var viewModel: ClaimDomainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClaimDomainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(false).colorRes(R.color.background).light(!isNightMode(this)).applyStatusBar()
        binding.root.addStatusBarTopPadding()
        binding.root.addNavigationBarBottomPadding()

        setupToolbar()
        setupUi()

        viewModel = ViewModelProvider(this)[ClaimDomainViewModel::class.java].apply {
            usernameLiveData.observe(this@ClaimDomainActivity) { binding.domainView.text = it }
            claimTransactionIdLiveData.observe(this@ClaimDomainActivity) { onTransactionIdReceive(it) }
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

    private fun setupUi() {
        binding.claimButton.setOnClickListener {
            viewModel.claim()
            binding.claimButton.setProgressVisible(true)
        }
    }

    private fun onTransactionIdReceive(tid: String?) {
        if (tid.isNullOrBlank()) {
            binding.claimButton.setProgressVisible(false)
            toast(msgRes = R.string.claim_domain_failed)
        } else {
            finish()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        title = R.string.free_domain.res2String()
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, ClaimDomainActivity::class.java))
        }
    }
}