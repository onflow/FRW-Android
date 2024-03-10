package com.flowfoundation.wallet.page.explore.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.database.Bookmark
import com.flowfoundation.wallet.page.explore.presenter.ExploreBookmarkItemPresenter

class ExploreBookmarkAdapter : BaseAdapter<Bookmark>(bookmarkDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ExploreBookmarkItemPresenter(parent.inflate(R.layout.item_explore_bookmark))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ExploreBookmarkItemPresenter).bind(getData()[position])
    }
}

private val bookmarkDiffCallback = object : DiffUtil.ItemCallback<Bookmark>() {
    override fun areItemsTheSame(oldItem: Bookmark, newItem: Bookmark): Boolean {
        return oldItem.url == newItem.url
    }

    override fun areContentsTheSame(oldItem: Bookmark, newItem: Bookmark): Boolean {
        return oldItem == newItem
    }
}