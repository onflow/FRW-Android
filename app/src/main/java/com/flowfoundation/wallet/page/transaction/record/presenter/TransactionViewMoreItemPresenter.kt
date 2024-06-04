package com.flowfoundation.wallet.page.transaction.record.presenter

import android.view.View
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.manager.app.isPreviewnet
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.page.browser.openBrowser
import com.flowfoundation.wallet.page.transaction.record.model.TransactionViewMoreModel
import com.flowfoundation.wallet.utils.findActivity

class TransactionViewMoreItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<TransactionViewMoreModel> {

    override fun bind(model: TransactionViewMoreModel) {
        view.setOnClickListener { openBrowser(findActivity(view)!!, model.url()) }
    }

    private fun TransactionViewMoreModel.url(): String {
        return if (isTestnet()) {
            "https://testnet.flowdiver.io/account/$address"
        } else if (isPreviewnet()) {
            "https://previewnet.flowdiver.io/account/$address"
        } else "https://flowdiver.io/account/$address"
    }
}