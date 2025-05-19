package com.flowfoundation.wallet.page.token.manage.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.flowfoundation.wallet.page.token.manage.presenter.ManageTokenItemPresenter

class ManageTokenAdapter : BaseAdapter<FungibleToken>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.layout_manage_token_item, parent, false)
        return ManageTokenItemPresenter(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        (holder as? ManageTokenItemPresenter)?.bind(item)
    }
}