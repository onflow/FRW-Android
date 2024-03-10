package com.flowfoundation.wallet.page.profile.subpage.wallet.key.presenter

import androidx.recyclerview.widget.LinearLayoutManager
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.ActivityAccountKeyBinding
import com.flowfoundation.wallet.page.profile.subpage.wallet.key.adapter.AccountKeyListAdapter
import com.flowfoundation.wallet.page.profile.subpage.wallet.key.model.AccountKey

class AccountKeyPresenter(
    binding: ActivityAccountKeyBinding,
) : BasePresenter<List<AccountKey>> {
    private val keyListAdapter by lazy { AccountKeyListAdapter() }

    init {
        with(binding.rvKeyList) {
            adapter = keyListAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }


    override fun bind(model: List<AccountKey>) {
        keyListAdapter.setNewDiffData(model)
    }
}