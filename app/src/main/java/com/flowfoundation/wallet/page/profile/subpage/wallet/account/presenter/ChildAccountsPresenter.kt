package com.flowfoundation.wallet.page.profile.subpage.wallet.account.presenter

import androidx.recyclerview.widget.LinearLayoutManager
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.ActivityChildAccountsBinding
import com.flowfoundation.wallet.manager.childaccount.ChildAccount
import com.flowfoundation.wallet.page.profile.subpage.wallet.account.ChildAccountsActivity
import com.flowfoundation.wallet.page.profile.subpage.wallet.account.adapter.ChildAccountListAdapter
import com.flowfoundation.wallet.page.profile.subpage.wallet.account.model.ChildAccountsModel
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration

class ChildAccountsPresenter(
    private val binding: ActivityChildAccountsBinding,
    private val activity: ChildAccountsActivity,
) : BasePresenter<ChildAccountsModel> {

    private val adapter by lazy { ChildAccountListAdapter() }

    init {
        with(binding.recyclerView) {
            adapter = this@ChildAccountsPresenter.adapter
            layoutManager = LinearLayoutManager(activity)
            addItemDecoration(ColorDividerItemDecoration(R.color.transparent.res2color(), 8.dp2px().toInt()))
        }

    }

    override fun bind(model: ChildAccountsModel) {
        model.accounts?.let { updateAccounts(it) }
    }

    private fun updateAccounts(accounts: List<ChildAccount>) {
        with(binding) {
            llEmpty.setVisible(accounts.isEmpty())
            recyclerView.setVisible(accounts.isEmpty().not())
        }
        adapter.setNewDiffData(accounts)
    }
}