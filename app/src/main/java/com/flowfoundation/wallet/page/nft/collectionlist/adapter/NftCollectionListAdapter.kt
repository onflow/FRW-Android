package com.flowfoundation.wallet.page.nft.collectionlist.adapter

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.page.nft.collectionlist.model.NftCollectionItem
import com.flowfoundation.wallet.page.nft.collectionlist.nftCollectionListDiffCallback
import com.flowfoundation.wallet.page.nft.collectionlist.presenter.NftCollectionItemPresenter

class NftCollectionListAdapter : BaseAdapter<Any>(nftCollectionListDiffCallback) {

    override fun getItemViewType(position: Int): Int {
        return TYPE_TOKEN
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_TOKEN -> NftCollectionItemPresenter(parent.inflate(R.layout.item_nft_collection_list))
            else -> BaseViewHolder(View(parent.context))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is NftCollectionItemPresenter -> holder.bind(getItem(position) as NftCollectionItem)
        }
    }

    companion object {
        private const val TYPE_TOKEN = 1
    }
}