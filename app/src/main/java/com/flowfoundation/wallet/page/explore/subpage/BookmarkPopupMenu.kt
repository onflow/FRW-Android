package com.flowfoundation.wallet.page.explore.subpage

import android.view.View
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.database.AppDataBase
import com.flowfoundation.wallet.database.Bookmark
import com.flowfoundation.wallet.utils.*
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.widgets.popup.PopupListView

class BookmarkPopupMenu(
    private val view: View,
    val bookmark: Bookmark,
) {

    private val activity = findActivity(view)

    fun show() {
        uiScope {
            popupMenu(
                view,
                context = activity,
                items = listOf(
                    if (bookmark.isFavourite) PopupListView.ItemData(
                        R.string.cancel_favourite.res2String(),
                        iconRes = R.drawable.ic_selection_star,
                        iconTint = R.color.salmon_primary.res2color(),
                    ) else PopupListView.ItemData(
                        R.string.favourite.res2String(),
                        iconRes = R.drawable.ic_collection_star,
                        iconTint = R.color.salmon_primary.res2color()
                    ),
                    PopupListView.ItemData(
                        R.string.delete.res2String(),
                        iconRes = R.drawable.ic_trash,
                        iconTint = R.color.salmon_primary.res2color()
                    ),
                ),
                offsetY = -view.height / 2,
                offsetX = ScreenUtils.getScreenWidth() / 4,
                selectListener = { _, text -> onMenuItemClick(text) },
                isDialogMode = true,
            ).show()
        }
    }

    private fun onMenuItemClick(text: String): Boolean {
        ioScope {
            when (text) {
                R.string.cancel_favourite.res2String() -> {
                    bookmark.isFavourite = false
                    AppDataBase.database().bookmarkDao().update(bookmark)
                }
                R.string.favourite.res2String() -> {
                    bookmark.isFavourite = true
                    AppDataBase.database().bookmarkDao().update(bookmark)
                }
                R.string.delete.res2String() -> AppDataBase.database().bookmarkDao().delete(bookmark)
            }
        }
        return true
    }
}