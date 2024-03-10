package com.flowfoundation.wallet.page.explore.presenter

import android.view.View
import android.widget.ImageView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.database.Bookmark
import com.flowfoundation.wallet.page.browser.loadFavicon
import com.flowfoundation.wallet.page.browser.openBrowser
import com.flowfoundation.wallet.page.browser.toFavIcon
import com.flowfoundation.wallet.page.explore.ExploreViewModel
import com.flowfoundation.wallet.utils.findActivity

class ExploreBookmarkItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<Bookmark> {
    private val iconView by lazy { view.findViewById<ImageView>(R.id.icon_view) }

    private val activity = findActivity(view)

    private val viewModel by lazy { ViewModelProvider(findActivity(view) as FragmentActivity)[ExploreViewModel::class.java] }

    override fun bind(model: Bookmark) {
        iconView.loadFavicon(model.url.toFavIcon())
        view.setOnClickListener {
            viewModel.onDAppClick(model.url)
            openBrowser(activity!!, model.url)
        }
    }
}