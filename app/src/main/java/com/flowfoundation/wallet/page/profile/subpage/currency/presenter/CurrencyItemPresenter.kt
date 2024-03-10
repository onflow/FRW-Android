package com.flowfoundation.wallet.page.profile.subpage.currency.presenter

import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemCurrencyListBinding
import com.flowfoundation.wallet.page.profile.subpage.currency.CurrencyViewModel
import com.flowfoundation.wallet.page.profile.subpage.currency.model.CurrencyItemModel
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.findActivity

class CurrencyItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<CurrencyItemModel> {

    private val viewModel by lazy { ViewModelProvider(findActivity(view) as FragmentActivity)[CurrencyViewModel::class.java] }

    private val binding by lazy { ItemCurrencyListBinding.bind(view) }

    override fun bind(model: CurrencyItemModel) {
        with(binding) {
            val currency = model.currency
            nameView.text = currency.name
            symbolView.text = currency.symbol
            iconView.setImageResource(currency.icon)
            stateButton.setVisible(model.isSelected)

            view.setOnClickListener { viewModel.updateFlag(currency.flag) }
        }
    }
}