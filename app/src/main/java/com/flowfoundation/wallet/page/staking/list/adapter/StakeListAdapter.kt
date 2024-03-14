package com.flowfoundation.wallet.page.staking.list.adapter

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.page.staking.list.model.StakingListItemModel
import com.flowfoundation.wallet.page.staking.list.presenter.StakingListItemPresenter

class StakeListAdapter : BaseAdapter<Any>(diffCallback) {

    override fun getItemViewType(position: Int): Int {
        return TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_ITEM -> StakingListItemPresenter(parent.inflate(R.layout.item_stake_list))
            else -> BaseViewHolder(View(parent.context))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is StakingListItemPresenter -> holder.bind(getItem(position) as StakingListItemModel)
        }
    }

    companion object {
        private const val TYPE_ITEM = 1
    }
}

private val diffCallback = object : DiffUtil.ItemCallback<Any>() {
    override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
        if (oldItem is StakingListItemModel && newItem is StakingListItemModel) {
            return oldItem.provider == newItem.provider
        }
        return oldItem == newItem
    }

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
        return oldItem == newItem
    }
}