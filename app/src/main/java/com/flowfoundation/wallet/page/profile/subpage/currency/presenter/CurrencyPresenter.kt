package com.flowfoundation.wallet.page.profile.subpage.currency.presenter

import androidx.recyclerview.widget.LinearLayoutManager
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.ActivitySettingsCurrencyBinding
import com.flowfoundation.wallet.page.profile.subpage.currency.adapter.CurrencyListAdapter
import com.flowfoundation.wallet.page.profile.subpage.currency.model.CurrencyModel

class CurrencyPresenter(
    private val binding: ActivitySettingsCurrencyBinding,
) : BasePresenter<CurrencyModel> {

    private val adapter by lazy { CurrencyListAdapter() }

    init {
        binding.root.addStatusBarTopPadding()
        setupRecyclerView()
    }

    override fun bind(model: CurrencyModel) {
        model.data?.let { adapter.setNewDiffData(it) }
    }

    private fun setupRecyclerView() {
        with(binding.recyclerView) {
            adapter = this@CurrencyPresenter.adapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        }
    }
}