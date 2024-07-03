package com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.crowdin.platform.util.inflateWithCrowdin
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityChildAccountDetailBinding
import com.flowfoundation.wallet.manager.childaccount.ChildAccount
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail.model.ChildAccountDetailModel
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail.presenter.ChildAccountDetailPresenter
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountedit.ChildAccountEditActivity
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.isNightMode

class ChildAccountDetailActivity : BaseActivity() {

    private val account by lazy { intent.getParcelableExtra<ChildAccount>(EXTRA_DATA) }
    private lateinit var binding: ActivityChildAccountDetailBinding
    private lateinit var presenter: ChildAccountDetailPresenter
    private lateinit var viewModel: ChildAccountDetailViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (account == null) {
            finish()
            return
        }
        binding = ActivityChildAccountDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        UltimateBarX.with(this).fitWindow(false).colorRes(R.color.background).light(!isNightMode(this)).applyStatusBar()
        binding.root.addStatusBarTopPadding()

        presenter = ChildAccountDetailPresenter(binding, this)
        account?.let { presenter.bind(ChildAccountDetailModel(account = account)) }

        viewModel = ViewModelProvider(this)[ChildAccountDetailViewModel::class.java].apply {
            nftCollectionsLiveData.observe(this@ChildAccountDetailActivity) { presenter.bind(ChildAccountDetailModel(nftCollections = it)) }
            coinListLiveData.observe(this@ChildAccountDetailActivity) {presenter.bind(ChildAccountDetailModel(coinList = it))}
            queryCollection(account!!)
            queryCoinList(account!!)
        }

        setupToolbar()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflateWithCrowdin(R.menu.child_account_detail, menu, resources)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.edit_action -> ChildAccountEditActivity.launch(this, account!!)
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
        private const val EXTRA_DATA = "extra_data"


        fun launch(context: Context, account: ChildAccount) {
            context.startActivity(Intent(context, ChildAccountDetailActivity::class.java).apply {
                putExtra(EXTRA_DATA, account)
            })
        }
    }
}