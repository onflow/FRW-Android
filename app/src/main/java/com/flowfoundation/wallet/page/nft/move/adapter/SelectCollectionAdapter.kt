package com.flowfoundation.wallet.page.nft.move.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.page.nft.move.model.CollectionDetailInfo
import com.flowfoundation.wallet.page.nft.move.presenter.SelectCollectionItemPresenter

class SelectCollectionAdapter(
    private val selectedCollectionId: String? = null,
    private val callback: (CollectionDetailInfo) -> Unit,
) : BaseAdapter<CollectionDetailInfo>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return SelectCollectionItemPresenter(parent.inflate(R.layout.item_select_collection_list), selectedCollectionId, callback)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SelectCollectionItemPresenter -> holder.bind(getItem(position))
        }
    }
}