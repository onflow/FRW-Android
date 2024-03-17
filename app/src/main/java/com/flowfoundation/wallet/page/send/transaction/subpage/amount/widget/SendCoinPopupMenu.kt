package com.flowfoundation.wallet.page.send.transaction.subpage.amount.widget

import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.page.send.transaction.subpage.amount.SendAmountViewModel
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.popupMenu
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.popup.PopupListView

class SendCoinPopupMenu(
    private val view: View,
) {
    private val viewModel by lazy { ViewModelProvider(findActivity(view) as FragmentActivity)[SendAmountViewModel::class.java] }

    fun show() {
        ioScope {
            val coinList = FlowCoinListManager.getEnabledCoinList()
            uiScope {
                popupMenu(
                    view,
                    items = coinList.map { PopupListView.ItemData(it.name, iconUrl = it.icon) },
                    selectListener = { _, text -> onMenuItemClick(text) },
                ).show()
            }
        }
    }

    private fun onMenuItemClick(text: String): Boolean {
        FlowCoinListManager.coinList().firstOrNull { it.name.lowercase() == text.lowercase() }?.let { viewModel.changeCoin(it) }
        return true
    }

}