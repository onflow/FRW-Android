package com.flowfoundation.wallet.page.guide.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.page.guide.model.GuideItemModel
import com.flowfoundation.wallet.page.guide.presenter.GuideItemPresenter

class GuideItemAdapter : BaseAdapter<GuideItemModel>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return GuideItemPresenter(parent.inflate(R.layout.item_guide_page))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as GuideItemPresenter).bind(getData()[position])
    }
}