package io.outblock.lilico.page.profile.subpage.wallet.key.presenter

import androidx.recyclerview.widget.LinearLayoutManager
import io.outblock.lilico.base.presenter.BasePresenter
import io.outblock.lilico.databinding.ActivityAccountKeyBinding
import io.outblock.lilico.page.profile.subpage.wallet.key.adapter.AccountKeyListAdapter
import io.outblock.lilico.page.profile.subpage.wallet.key.model.AccountKey

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