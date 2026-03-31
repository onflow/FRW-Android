package com.flowfoundation.wallet.page.transaction.record.presenter

import android.view.View
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.network.explorerUrl
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
        val chain = if (EVMWalletManager.isEVMWalletAddress(address)) "evm" else "flow"
        val type = if (chain == "evm") "address" else "account"
        return explorerUrl(id = address, type = type, chain = chain)
    }
}