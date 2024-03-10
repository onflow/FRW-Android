package com.flowfoundation.wallet.page.inbox.adapter

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.network.model.InboxNft
import com.flowfoundation.wallet.page.inbox.presenter.InboxNftItemPresenter

class InboxNftAdapter : BaseAdapter<InboxNft>(diffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return InboxNftItemPresenter(parent.inflate(R.layout.item_inbox_nft))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as InboxNftItemPresenter).bind(getItem(position))
    }
}

private val diffCallback = object : DiffUtil.ItemCallback<InboxNft>() {
    override fun areItemsTheSame(oldItem: InboxNft, newItem: InboxNft): Boolean {
        return oldItem.collectionAddress == newItem.collectionAddress && oldItem.tokenId == newItem.tokenId
    }

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: InboxNft, newItem: InboxNft): Boolean {
        return oldItem == newItem
    }
}