package com.flowfoundation.wallet.page.token.manage

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityManageTokenBinding
import com.flowfoundation.wallet.page.token.manage.presenter.ManageTokenPresenter
import com.flowfoundation.wallet.page.token.manage.viewmodel.ManageTokenViewModel
import com.flowfoundation.wallet.utils.isNightMode
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX


class ManageTokenActivity: BaseActivity() {
    private lateinit var binding: ActivityManageTokenBinding
    private lateinit var viewModel: ManageTokenViewModel
    private lateinit var presenter: ManageTokenPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageTokenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(true).light(!isNightMode(this)).applyNavigationBar()

        presenter = ManageTokenPresenter(this, binding)
        viewModel = ViewModelProvider(this)[ManageTokenViewModel::class.java].apply {
            tokenListLiveData.observe(this@ManageTokenActivity) {
                presenter.bind(it)
            }
            tokenIndexLiveData.observe(this@ManageTokenActivity) {
                presenter.notifyTokenItemChanged(it)
            }

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

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, ManageTokenActivity::class.java))
        }
    }

}