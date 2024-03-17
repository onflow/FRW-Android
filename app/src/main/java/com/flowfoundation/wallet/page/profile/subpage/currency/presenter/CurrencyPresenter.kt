package com.flowfoundation.wallet.page.profile.subpage.currency.presenter

import android.graphics.Color
import android.transition.Scene
import android.transition.Slide
import android.transition.TransitionManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.ActivityAddTokenBinding
import com.flowfoundation.wallet.databinding.ActivitySettingsCurrencyBinding
import com.flowfoundation.wallet.page.profile.subpage.currency.CurrencyListActivity
import com.flowfoundation.wallet.page.profile.subpage.currency.CurrencyViewModel
import com.flowfoundation.wallet.page.profile.subpage.currency.adapter.CurrencyListAdapter
import com.flowfoundation.wallet.page.profile.subpage.currency.model.CurrencyModel
import com.flowfoundation.wallet.page.token.addtoken.AddTokenActivity
import com.flowfoundation.wallet.page.token.addtoken.AddTokenViewModel
import com.flowfoundation.wallet.page.token.addtoken.adapter.TokenListAdapter
import com.flowfoundation.wallet.page.token.addtoken.model.AddTokenModel
import com.flowfoundation.wallet.utils.extensions.*
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration

class CurrencyPresenter(
    private val activity: CurrencyListActivity,
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