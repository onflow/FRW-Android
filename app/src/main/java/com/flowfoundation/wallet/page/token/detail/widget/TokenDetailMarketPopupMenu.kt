package com.flowfoundation.wallet.page.token.detail.widget

import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.page.token.detail.QuoteMarket
import com.flowfoundation.wallet.page.token.detail.TokenDetailViewModel
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.utils.popupMenu
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.popup.PopupListView

class TokenDetailMarketPopupMenu(
    private val view: View,
    private val callback: (market: String) -> Unit,
) {
    private val viewModel by lazy { ViewModelProvider(findActivity(view) as FragmentActivity)[TokenDetailViewModel::class.java] }

    fun show() {
        uiScope {
            popupMenu(
                view,
                items = listOf(
                    PopupListView.ItemData(R.string.market_binance.res2String(), iconRes = R.drawable.ic_market_binance),
                    PopupListView.ItemData(R.string.market_kraken.res2String(), iconRes = R.drawable.ic_market_kraken),
                    PopupListView.ItemData(R.string.market_huobi.res2String(), iconRes = R.drawable.ic_market_huobi),
                ),
                selectListener = { _, text -> onMenuItemClick(text) },
            ).show()
        }
    }

    private fun onMenuItemClick(text: String): Boolean {
        when (text) {
            R.string.market_binance.res2String() -> viewModel.changeMarket(QuoteMarket.binance.value)
            R.string.market_kraken.res2String() -> viewModel.changeMarket(QuoteMarket.kraken.value)
            R.string.market_huobi.res2String() -> viewModel.changeMarket(QuoteMarket.huobi.value)
        }
        callback.invoke(text)
        return true
    }

}