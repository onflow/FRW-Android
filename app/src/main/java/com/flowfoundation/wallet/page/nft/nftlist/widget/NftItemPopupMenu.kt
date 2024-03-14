package com.flowfoundation.wallet.page.nft.nftlist.widget

import android.view.View
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.popupMenu
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.popup.PopupListView

class NftItemPopupMenu(
    private val view: View,
    val nft: Nft,
) {

    fun show() {
        uiScope {
            popupMenu(
                view,
                items = listOf(
                    PopupListView.ItemData(R.string.top_selection.res2String(), iconRes = R.drawable.ic_selection_star),
                    PopupListView.ItemData(R.string.share.res2String(), iconRes = R.drawable.ic_share),
                    PopupListView.ItemData(R.string.send.res2String(), iconRes = R.drawable.ic_send_simple),
                ),
                selectListener = { _, text -> onMenuItemClick(text) },
            ).show()
        }
    }

    private fun onMenuItemClick(text: String): Boolean {
        when (text) {
            R.id.action_select.res2String() -> {}
            R.id.action_share.res2String() -> {}
            R.id.action_send.res2String() -> {}
        }
        return true
    }
}