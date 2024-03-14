package com.flowfoundation.wallet.page.explore.presenter

import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.database.WebviewRecord
import com.flowfoundation.wallet.databinding.ItemExploreRecentBinding
import com.flowfoundation.wallet.page.browser.*
import com.flowfoundation.wallet.page.explore.ExploreViewModel
import com.flowfoundation.wallet.utils.findActivity

class ExploreRecentItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<WebviewRecord> {
    private val binding by lazy { ItemExploreRecentBinding.bind(view) }

    private val activity = findActivity(view)

    private val viewModel by lazy { ViewModelProvider(findActivity(view) as FragmentActivity)[ExploreViewModel::class.java] }

    override fun bind(model: WebviewRecord) {
        with(binding) {
            Glide.with(coverView).load(screenshotFile(screenshotFileName(model.url))).into(coverView)
            iconView.loadFavicon(model.url.toFavIcon())
            titleView.text = model.title

            view.setOnClickListener {
                viewModel.onDAppClick(model.url)
                openBrowser(activity!!, model.url)
            }
        }
    }
}