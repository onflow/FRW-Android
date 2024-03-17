package com.flowfoundation.wallet.page.browser.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.page.browser.model.RecommendModel
import com.flowfoundation.wallet.page.browser.presenter.BrowserRecommendWordPresenter

class BrowserRecommendWordsAdapter : BaseAdapter<RecommendModel>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return BrowserRecommendWordPresenter(parent.inflate(R.layout.item_browser_recommend_word))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as BrowserRecommendWordPresenter).bind(getItem(position))
    }
}