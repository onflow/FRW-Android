package com.flowfoundation.wallet.page.explore.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.page.explore.model.DAppModel
import com.flowfoundation.wallet.page.explore.presenter.ExploreDAppItemPresenter

class ExploreDAppAdapter : BaseAdapter<DAppModel>(diffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ExploreDAppItemPresenter(parent.inflate(R.layout.item_explore_dapp))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ExploreDAppItemPresenter).bind(getData()[position])
    }
}

private val diffCallback = object : DiffUtil.ItemCallback<DAppModel>() {
    override fun areItemsTheSame(oldItem: DAppModel, newItem: DAppModel): Boolean {
        return oldItem.url == newItem.url
    }

    override fun areContentsTheSame(oldItem: DAppModel, newItem: DAppModel): Boolean {
        return oldItem == newItem
    }
}