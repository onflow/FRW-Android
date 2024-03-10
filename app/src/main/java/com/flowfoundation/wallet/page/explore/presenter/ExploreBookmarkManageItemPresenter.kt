package com.flowfoundation.wallet.page.explore.presenter

import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.database.Bookmark
import com.flowfoundation.wallet.databinding.ItemExploreBookmarkManageBinding
import com.flowfoundation.wallet.page.browser.loadFavicon
import com.flowfoundation.wallet.page.browser.openBrowser
import com.flowfoundation.wallet.page.browser.toFavIcon
import com.flowfoundation.wallet.page.explore.ExploreViewModel
import com.flowfoundation.wallet.page.explore.subpage.BookmarkPopupMenu
import com.flowfoundation.wallet.utils.extensions.urlHost
import com.flowfoundation.wallet.utils.findActivity

class ExploreBookmarkManageItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<Bookmark> {
    private val binding by lazy { ItemExploreBookmarkManageBinding.bind(view) }

    private val activity = findActivity(view)

    private val viewModel by lazy { ViewModelProvider(findActivity(view) as FragmentActivity)[ExploreViewModel::class.java] }

    override fun bind(model: Bookmark) {
        with(binding) {
            iconView.loadFavicon(model.url.toFavIcon())
            titleView.text = model.title
            domainView.text = model.url.urlHost()
        }
        view.setOnClickListener {
            viewModel.onDAppClick(model.url)
            openBrowser(activity!!, model.url)
        }
        view.setOnLongClickListener {
            BookmarkPopupMenu(view, model).show()
            true
        }
    }
}