package com.flowfoundation.wallet.page.profile.subpage.developer.adapter

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.page.profile.subpage.developer.model.LocalAccountKey
import com.flowfoundation.wallet.page.profile.subpage.developer.presenter.LocalAccountKeyListItemPresenter
import com.flowfoundation.wallet.page.profile.subpage.developer.presenter.LocalAccountKeyListTitlePresenter

class LocalAccountKeyAdapter : BaseAdapter<Any>() {

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is Int -> TYPE_TITLE
            is LocalAccountKey -> TYPE_ITEM
            else -> -1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_TITLE -> LocalAccountKeyListTitlePresenter(parent.inflate(R.layout.layout_local_key_title))
            TYPE_ITEM -> LocalAccountKeyListItemPresenter(parent.inflate(R.layout.layout_local_key_item))
            else -> BaseViewHolder(View(parent.context))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is LocalAccountKeyListTitlePresenter -> holder.bind(getItem(position) as Int)
            is LocalAccountKeyListItemPresenter -> holder.bind(getItem(position) as LocalAccountKey)
        }
    }

    companion object {
        private const val TYPE_TITLE = 0
        private const val TYPE_ITEM = 1
    }
}