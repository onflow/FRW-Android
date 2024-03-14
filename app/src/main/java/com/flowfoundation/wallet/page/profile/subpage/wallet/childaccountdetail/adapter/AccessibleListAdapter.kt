package com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail.adapter

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail.CoinData
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail.CollectionData
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail.presenter.AccessibleCoinPresenter
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail.presenter.AccessibleNftCollectionPresenter

class AccessibleListAdapter : BaseAdapter<Any>(diffUtils) {

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is CollectionData -> TYPE_NFT
            is CoinData -> TYPE_TOKEN
            else -> TYPE_TOKEN
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_NFT -> AccessibleNftCollectionPresenter(parent.inflate(R.layout.item_accessible_nft))
            TYPE_TOKEN -> AccessibleCoinPresenter(parent.inflate(R.layout.item_accessible_coin))
            else -> BaseViewHolder(View(parent.context))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is AccessibleNftCollectionPresenter -> holder.bind(getItem(position) as CollectionData)
            is AccessibleCoinPresenter -> holder.bind(getItem(position) as CoinData)
        }
    }

    companion object {
        private const val TYPE_NFT = 1
        private const val TYPE_TOKEN = 2
    }
}

private val diffUtils = object : DiffUtil.ItemCallback<Any>() {
    override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
        if (oldItem is CollectionData && newItem is CollectionData) {
            return oldItem.id == newItem.id
        }
        if (oldItem is CoinData && newItem is CoinData) {
            return oldItem.name == newItem.name
        }
        return oldItem == newItem
    }

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
        if (oldItem is CollectionData && newItem is CollectionData) {
            return oldItem == newItem
        }
        if (oldItem is CoinData && newItem is CoinData) {
            return oldItem == newItem
        }
        return oldItem == newItem
    }
}