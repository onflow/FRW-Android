package com.flowfoundation.wallet.page.profile.subpage.wallet.account

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityChildAccountsBinding
import com.flowfoundation.wallet.page.profile.subpage.wallet.account.model.ChildAccountsModel
import com.flowfoundation.wallet.page.profile.subpage.wallet.account.presenter.ChildAccountsPresenter
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.isNightMode

class ChildAccountsActivity : BaseActivity() {

    private lateinit var binding: ActivityChildAccountsBinding
    private lateinit var presenter: ChildAccountsPresenter
    private lateinit var viewModel: ChildAccountsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChildAccountsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        UltimateBarX.with(this).fitWindow(false).colorRes(R.color.background).light(!isNightMode(this)).applyStatusBar()
        binding.root.addStatusBarTopPadding()

        presenter = ChildAccountsPresenter(binding, this)

        viewModel = ViewModelProvider(this)[ChildAccountsViewModel::class.java].apply {
            accountsLiveData.observe(this@ChildAccountsActivity) { presenter.bind(ChildAccountsModel(accounts = it)) }
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
        title = R.string.linked_account.res2String()
    }

    companion object {

        fun launch(context: Context) {
            context.startActivity(Intent(context, ChildAccountsActivity::class.java))
        }
    }
}