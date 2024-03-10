package com.flowfoundation.wallet.page.window.bubble.presenter

import android.view.View
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemBrowserFloatTabsBinding
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionState.Companion.TYPE_ADD_TOKEN
import com.flowfoundation.wallet.manager.transaction.TransactionState.Companion.TYPE_FCL_TRANSACTION
import com.flowfoundation.wallet.manager.transaction.TransactionState.Companion.TYPE_TRANSFER_COIN
import com.flowfoundation.wallet.manager.transaction.TransactionState.Companion.TYPE_TRANSFER_NFT
import com.flowfoundation.wallet.page.browser.browserViewModel
import com.flowfoundation.wallet.page.browser.expandBrowser
import com.flowfoundation.wallet.page.browser.openInFlowScan
import com.flowfoundation.wallet.page.browser.tools.BrowserTab
import com.flowfoundation.wallet.page.browser.tools.changeBrowserTab
import com.flowfoundation.wallet.page.dialog.processing.coinenable.CoinEnableProcessingDialog
import com.flowfoundation.wallet.page.dialog.processing.fcl.FclTransactionProcessingDialog
import com.flowfoundation.wallet.page.dialog.processing.send.SendProcessingDialog
import com.flowfoundation.wallet.page.window.bubble.bubbleViewModel
import com.flowfoundation.wallet.page.window.bubble.model.BubbleItem
import com.flowfoundation.wallet.page.window.bubble.model.icon
import com.flowfoundation.wallet.page.window.bubble.model.title
import com.flowfoundation.wallet.page.window.bubble.tools.popBubbleStack
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.findActivity

class FloatTabsItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<BubbleItem> {

    private val binding by lazy { ItemBrowserFloatTabsBinding.bind(view) }

    override fun bind(model: BubbleItem) {
        with(binding) {
            BaseActivity.getCurrentActivity()?.let { Glide.with(it).load(model.icon()).into(iconView) }
            titleView.text = model.title()
            progressBar.setVisible(model.data is TransactionState)
            (model.data as? TransactionState)?.let { progressBar.setProgress((it.progress() * 100).toInt(), true) }
            closeButton.setOnClickListener { popBubbleStack(model.data) }
            contentView.setOnClickListener {
                bubbleViewModel()?.onHideFloatTabs()
                showTabContent(model.data)
            }
        }
    }

    private fun showTabContent(data: Any) {
        when (data) {
            is BrowserTab -> showBrowser(data)
            is TransactionState -> showTransactionStateDialog(data)
        }
    }

    private fun showTransactionStateDialog(data: TransactionState) {
        when (data.type) {
            TYPE_TRANSFER_COIN, TYPE_TRANSFER_NFT -> SendProcessingDialog.show(data)
            TYPE_ADD_TOKEN -> CoinEnableProcessingDialog.show(data)
            TYPE_FCL_TRANSACTION -> FclTransactionProcessingDialog.show(data.transactionId)
            else -> openInFlowScan(findActivity(view)!!, data.transactionId)
        }
    }

    private fun showBrowser(tab: BrowserTab) {
        changeBrowserTab(tab.id)
        browserViewModel()?.onTabChange()
        expandBrowser()
    }
}