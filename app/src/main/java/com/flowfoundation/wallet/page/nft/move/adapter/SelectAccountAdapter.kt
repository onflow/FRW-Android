package com.flowfoundation.wallet.page.nft.move.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.page.nft.move.presenter.SelectAccountPresenter

class SelectAccountAdapter(
    private val selectedAddress: String,
    private val callback: (String) -> Unit
) : BaseAdapter<String>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return SelectAccountPresenter(
            parent.inflate(
                R.layout.item_select_account_list
            ), selectedAddress, callback
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as SelectAccountPresenter).bind(getItem(position))
    }


}