package com.flowfoundation.wallet.page.token.custom.presenter

import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.page.token.custom.model.CustomTokenItem


class AddCustomTokenPresenter: BasePresenter<List<CustomTokenItem>> {

    init {

    }

    override fun bind(model: List<CustomTokenItem>) {
        if (model.isEmpty()) {

        } else if (model.size == 1) {

        } else {

        }
    }
}