package com.flowfoundation.wallet.page.explore.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.database.WebviewRecord
import com.flowfoundation.wallet.page.explore.exploreRecentDiffCallback
import com.flowfoundation.wallet.page.explore.presenter.ExploreRecentItemPresenter
import com.flowfoundation.wallet.utils.ScreenUtils

class ExploreRecentAdapter(
    private val isHorizontal: Boolean
) : BaseAdapter<WebviewRecord>(exploreRecentDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = parent.inflate(R.layout.item_explore_recent)
        if (isHorizontal) {
            view.layoutParams = ViewGroup.LayoutParams((ScreenUtils.getScreenWidth() / 2.5f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        return ExploreRecentItemPresenter(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ExploreRecentItemPresenter).bind(getData()[position])
    }
}