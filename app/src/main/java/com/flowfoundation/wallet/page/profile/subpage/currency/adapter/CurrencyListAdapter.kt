package com.flowfoundation.wallet.page.profile.subpage.currency.adapter

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.page.profile.subpage.currency.model.CurrencyItemModel
import com.flowfoundation.wallet.page.profile.subpage.currency.presenter.CurrencyItemPresenter

class CurrencyListAdapter : BaseAdapter<Any>(diffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return CurrencyItemPresenter(parent.inflate(R.layout.item_currency_list))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as CurrencyItemPresenter).bind(getItem(position) as CurrencyItemModel)
    }
}

private val diffCallback = object : DiffUtil.ItemCallback<Any>() {
    override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
        if (oldItem is CurrencyItemModel && newItem is CurrencyItemModel) {
            return oldItem.currency.symbol == newItem.currency.symbol
        }
        return oldItem == newItem
    }

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
        if (oldItem is CurrencyItemModel && newItem is CurrencyItemModel) {
            return oldItem == newItem
        }
        return oldItem == newItem
    }
}