package com.flowfoundation.wallet.page.nft.nftlist.adapter

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.page.nft.nftlist.isSingleNftItem
import com.flowfoundation.wallet.page.nft.nftlist.model.*
import com.flowfoundation.wallet.page.nft.nftlist.nftListDiffCallback
import com.flowfoundation.wallet.page.nft.nftlist.presenter.*

class NFTListAdapter : BaseAdapter<Any>(nftListDiffCallback) {

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is NFTItemModel -> TYPE_NFT
            is NFTTitleModel -> TYPE_TITLE
            is HeaderPlaceholderModel -> TYPE_GRID_HEADER
            is CollectionTitleModel -> TYPE_COLLECTION_TITLE
            is CollectionItemModel -> TYPE_COLLECTION_LINE
            is CollectionTabsModel -> TYPE_COLLECTION_TABS
            is NFTCountTitleModel -> TYPE_COUNT_TITLE
            is NftLoadMoreModel -> TYPE_LOAD_MORE
            is NftItemShimmerModel -> TYPE_PLACE_HOLDER
            else -> TYPE_NONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_NFT -> NFTListItemPresenter(parent.inflate(R.layout.item_nft_list))
            TYPE_TITLE -> TitleItemPresenter(parent.inflate(R.layout.item_nft_list_title))
            TYPE_GRID_HEADER -> HeaderPlaceholderPresenter(parent.inflate(R.layout.item_nft_list_grid_header_placeholder))
            TYPE_COLLECTION_TITLE -> CollectionTitlePresenter(parent.inflate(R.layout.item_nft_list_collection_title))
            TYPE_COLLECTION_LINE -> CollectionLineItemPresenter(parent.inflate(R.layout.item_nft_list_collection_line))
            TYPE_COLLECTION_TABS -> CollectionTabsPresenter(parent.inflate(R.layout.item_nft_list_collection_tabs))
            TYPE_COUNT_TITLE -> NftCountTitlePresenter(parent.inflate(R.layout.item_nft_count_title))
            TYPE_LOAD_MORE -> NftLoadMorePresenter(parent.inflate(R.layout.item_nft_load_more))
            TYPE_PLACE_HOLDER -> NftItemShimmerPresenter(parent.inflate(R.layout.layout_shimmer_nft_item))
            else -> BaseViewHolder(View(parent.context))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is NFTListItemPresenter -> holder.bind(getItem(position) as NFTItemModel)
            is TitleItemPresenter -> holder.bind(getItem(position) as NFTTitleModel)
            is CollectionLineItemPresenter -> holder.bind(getItem(position) as CollectionItemModel)
            is CollectionTabsPresenter -> holder.bind(getItem(position) as CollectionTabsModel)
            is CollectionTitlePresenter -> holder.bind(getItem(position) as CollectionTitleModel)
            is HeaderPlaceholderPresenter -> holder.bind(getItem(position) as HeaderPlaceholderModel)
            is NftCountTitlePresenter -> holder.bind(getItem(position) as NFTCountTitleModel)
            is NftLoadMorePresenter -> holder.bind(getItem(position) as NftLoadMoreModel)
            is NftItemShimmerPresenter -> holder.bind(getItem(position) as NftItemShimmerModel)
        }
    }

    fun isSingleLineItem(position: Int): Boolean {
        val item = getData().getOrNull(position) ?: return false
        return isSingleNftItem(item)
    }

    companion object {
        private const val TYPE_NONE = -1
        private const val TYPE_NFT = 1
        private const val TYPE_TITLE = 2
        private const val TYPE_GRID_HEADER = 3
        private const val TYPE_COLLECTION_TITLE = 5
        private const val TYPE_COLLECTION_LINE = 6
        private const val TYPE_COLLECTION_TABS = 7
        private const val TYPE_COUNT_TITLE = 8
        private const val TYPE_LOAD_MORE = 9
        private const val TYPE_PLACE_HOLDER = 10
    }
}