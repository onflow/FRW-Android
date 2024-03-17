package com.flowfoundation.wallet.page.browser.widgets

import android.view.View
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.database.AppDataBase
import com.flowfoundation.wallet.database.Bookmark
import com.flowfoundation.wallet.page.browser.tools.BrowserTab
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.openInSystemBrowser
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.popupMenu
import com.flowfoundation.wallet.utils.shareText
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.popup.PopupListView

class BrowserPopupMenu(
    private val view: View,
    private val browserTab: BrowserTab,
) {
    fun show() {
        uiScope {
            val iconColor = R.color.salmon_primary.res2color()
            popupMenu(
                view,
                items = listOf(
                    PopupListView.ItemData(R.string.bookmark.res2String(), iconRes = R.drawable.ic_bookmark, iconTint = iconColor),
                    PopupListView.ItemData(R.string.share.res2String(), iconRes = R.drawable.ic_web, iconTint = iconColor),
                    PopupListView.ItemData(R.string.open_in_browser.res2String(), iconRes = R.drawable.ic_web, iconTint = iconColor),
                ),
                selectListener = { _, text -> onMenuItemClick(text) },
                offsetX = -50.dp2px().toInt()
            ).show()
        }
    }

    private fun onMenuItemClick(text: String): Boolean {
        when (text) {
            R.string.bookmark.res2String() -> addToBookmark()
            R.string.share.res2String() -> browserTab.url()?.let { view.context.shareText(it) }
            R.string.open_in_browser.res2String() -> browserTab.url()?.openInSystemBrowser(view.context)
        }
        return true
    }

    private fun addToBookmark() {
        val url = browserTab.url().orEmpty()
        val title = browserTab.title().orEmpty()
        ioScope {
            AppDataBase.database().bookmarkDao().save(
                Bookmark(
                    url = url,
                    title = title,
                    createTime = System.currentTimeMillis(),
                )
            )
        }
    }

}