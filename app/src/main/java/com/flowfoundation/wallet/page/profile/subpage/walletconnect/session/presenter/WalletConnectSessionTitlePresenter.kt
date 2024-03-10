package com.flowfoundation.wallet.page.profile.subpage.walletconnect.session.presenter

import android.view.View
import android.widget.TextView
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.page.profile.subpage.walletconnect.session.model.WalletConnectSessionTitleModel

class WalletConnectSessionTitlePresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<WalletConnectSessionTitleModel> {

    override fun bind(model: WalletConnectSessionTitleModel) {
        (view as TextView).text = model.title
    }
}