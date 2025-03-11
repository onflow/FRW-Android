package com.flowfoundation.wallet.page.explore.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.page.explore.model.DAppTagModel
import com.flowfoundation.wallet.page.explore.presenter.ExploreDAppTagItemPresenter

class ExploreDAppTagsAdapter : BaseAdapter<DAppTagModel>(diffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ExploreDAppTagItemPresenter(parent.inflate(R.layout.item_dapp_category))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ExploreDAppTagItemPresenter).bind(getData()[position])
    }
}

private val diffCallback = object : DiffUtil.ItemCallback<DAppTagModel>() {
    override fun areItemsTheSame(oldItem: DAppTagModel, newItem: DAppTagModel): Boolean {
        return oldItem.category == newItem.category
    }

    override fun areContentsTheSame(oldItem: DAppTagModel, newItem: DAppTagModel): Boolean {
        return oldItem == newItem
    }
}