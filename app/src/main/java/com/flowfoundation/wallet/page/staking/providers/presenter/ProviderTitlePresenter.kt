package com.flowfoundation.wallet.page.staking.providers.presenter

import android.view.View
import android.widget.TextView
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.page.staking.providers.model.ProviderTitleModel

class ProviderTitlePresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<ProviderTitleModel> {

    override fun bind(model: ProviderTitleModel) {
        (view as? TextView)?.text = model.title
    }
}