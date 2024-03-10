package com.flowfoundation.wallet.page.profile.subpage.wallet.key

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityAccountKeyBinding
import com.flowfoundation.wallet.page.profile.subpage.wallet.key.presenter.AccountKeyPresenter
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.isNightMode

class AccountKeyActivity : BaseActivity()  {

    private lateinit var binding: ActivityAccountKeyBinding
    private lateinit var viewModel: AccountKeyViewModel
    private lateinit var presenter: AccountKeyPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountKeyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        UltimateBarX.with(this).fitWindow(false).colorRes(R.color.background).light(!isNightMode(this)).applyStatusBar()
        binding.root.addStatusBarTopPadding()

        presenter = AccountKeyPresenter(binding)
        viewModel = ViewModelProvider(this)[AccountKeyViewModel::class.java].apply {
            keyListLiveData.observe(this@AccountKeyActivity) {
                presenter.bind(it)
            }
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
        title = R.string.account_keys.res2String()
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(launchIntent(context))
        }

        fun launchIntent(context: Context): Intent = Intent(context, AccountKeyActivity::class.java)
    }
}