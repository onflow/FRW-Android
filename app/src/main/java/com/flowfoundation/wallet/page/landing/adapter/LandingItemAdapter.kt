package com.flowfoundation.wallet.page.landing.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.page.landing.model.LandingItemModel
import com.flowfoundation.wallet.page.landing.presenter.LandingItemPresenter


class LandingItemAdapter : BaseAdapter<LandingItemModel>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return LandingItemPresenter(parent.inflate(R.layout.item_landing_page))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as LandingItemPresenter).bind(getData()[position])
    }

    override fun getItemCount(): Int {
        return getData().size
    }
}