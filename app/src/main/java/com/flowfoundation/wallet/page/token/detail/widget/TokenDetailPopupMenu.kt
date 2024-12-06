package com.flowfoundation.wallet.page.token.detail.widget

import android.view.View
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.manager.coin.CustomTokenManager
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.popupMenu
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.popup.PopupListView


class TokenDetailPopupMenu(
    private val view: View,
    private val coin: FlowCoin,
) {
    fun show() {
        uiScope {
            val iconColor = R.color.text_1.res2color()
            popupMenu(
                view,
                items = listOf(
                    PopupListView.ItemData(getDeleteText(), iconRes = R.drawable.ic_trash, iconTint = iconColor),
                ),
                selectListener = { _, text -> onMenuItemClick(text) },
            ).show()
        }
    }

    private fun getDeleteText(): String {
        return "${R.string.delete.res2String()} ${coin.symbol}"
    }

    private fun onMenuItemClick(text: String): Boolean {
        when (text) {
            getDeleteText() ->  {
                CustomTokenManager.deleteCustomToken(coin)
                uiScope {
                    val activity = BaseActivity.getCurrentActivity() ?: return@uiScope
                    activity.finish()
                }
            }
        }
        return true
    }
}