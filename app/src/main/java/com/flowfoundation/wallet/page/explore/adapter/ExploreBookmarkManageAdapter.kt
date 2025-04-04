package com.flowfoundation.wallet.page.explore.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.database.Bookmark
import com.flowfoundation.wallet.page.explore.model.BookmarkTitleModel
import com.flowfoundation.wallet.page.explore.presenter.ExploreBookmarkManageItemPresenter
import com.flowfoundation.wallet.page.explore.presenter.ExploreBookmarkTitleItemPresenter

class ExploreBookmarkManageAdapter : BaseAdapter<Any>(bookmarkDiffCallback) {
    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is BookmarkTitleModel -> 0
            else -> 1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            0 -> ExploreBookmarkTitleItemPresenter(parent.inflate(R.layout.item_explore_bookmark_title))
            else -> ExploreBookmarkManageItemPresenter(parent.inflate(R.layout.item_explore_bookmark_manage))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ExploreBookmarkTitleItemPresenter -> holder.bind(getData()[position] as BookmarkTitleModel)
            is ExploreBookmarkManageItemPresenter -> holder.bind(getData()[position] as Bookmark)
        }
    }
}

private val bookmarkDiffCallback = object : DiffUtil.ItemCallback<Any>() {
    override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
        if (oldItem is Bookmark && newItem is Bookmark) {
            return oldItem.id == newItem.id
        }
        return false
    }

    override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
        return false
    }
}