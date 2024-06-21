package com.flowfoundation.wallet.page.landing.presenter

import android.view.View
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemLandingPageBinding
import com.flowfoundation.wallet.page.landing.model.LandingItemModel


class LandingItemPresenter(
    private val view: View
): BaseViewHolder(view), BasePresenter<LandingItemModel> {

    private val binding by lazy {
        ItemLandingPageBinding.bind(view)
    }

    override fun bind(model: LandingItemModel) {
        with(binding) {
            ivLogo.setImageResource(model.logo)
            tvTitle.setText(model.title)
            tvDesc.setText(model.desc)
        }
    }
}