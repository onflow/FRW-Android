package com.flowfoundation.wallet.page.inbox

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityInboxBinding
import com.flowfoundation.wallet.page.inbox.model.InboxPageModel
import com.flowfoundation.wallet.page.inbox.presenter.InboxPagePresenter
import com.flowfoundation.wallet.utils.isNightMode

class InboxActivity : BaseActivity() {

    private lateinit var binding: ActivityInboxBinding
    private lateinit var presenter: InboxPagePresenter
    private lateinit var viewModel: InboxViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInboxBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(true).colorRes(R.color.background).light(!isNightMode(this)).applyStatusBar()
        setupToolbar()

        presenter = InboxPagePresenter(binding, this)
        viewModel = ViewModelProvider(this)[InboxViewModel::class.java].apply {
            tokenListLiveData.observe(this@InboxActivity) { presenter.bind(InboxPageModel(tokenList = it)) }
            nftListLiveData.observe(this@InboxActivity) { presenter.bind(InboxPageModel(nftList = it)) }
            claimExecutingLiveData.observe(this@InboxActivity) { presenter.bind(InboxPageModel(claimExecuting = it)) }
            query()
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
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, InboxActivity::class.java))
        }
    }
}