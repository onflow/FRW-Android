package com.flowfoundation.wallet.page.guide.presenter

import android.view.View
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemGuidePageBinding
import com.flowfoundation.wallet.page.guide.model.GuideItemModel

class GuideItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<GuideItemModel> {

    private val binding by lazy { ItemGuidePageBinding.bind(view) }

    override fun bind(model: GuideItemModel) {
        with(binding) {
            Glide.with(coverView).load(model.cover).into(coverView)
            titleView.setText(model.title)
            descView.setText(model.desc)
        }
    }
}