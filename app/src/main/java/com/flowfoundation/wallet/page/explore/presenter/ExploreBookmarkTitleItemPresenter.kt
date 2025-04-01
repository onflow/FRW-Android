package com.flowfoundation.wallet.page.explore.presenter

import android.view.View
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemExploreBookmarkTitleBinding
import com.flowfoundation.wallet.page.explore.model.BookmarkTitleModel

class ExploreBookmarkTitleItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<BookmarkTitleModel> {

    private val binding by lazy { ItemExploreBookmarkTitleBinding.bind(view) }

    override fun bind(model: BookmarkTitleModel) {
        with(binding) {
            titleView.text = model.title
            iconView.setImageResource(model.icon)
        }
    }
}