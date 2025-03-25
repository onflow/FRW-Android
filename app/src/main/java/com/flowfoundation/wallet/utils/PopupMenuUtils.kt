package com.flowfoundation.wallet.utils

import android.content.Context
import android.view.View
import com.lxj.xpopup.core.AttachPopupView
import com.lxj.xpopup.interfaces.OnSelectListener
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.page.main.widget.NetworkPopupListView
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.widgets.popup.PopupBuilder
import com.flowfoundation.wallet.widgets.popup.PopupListView

fun popupMenu(
    author: View,
    context: Context? = null,
    items: List<PopupListView.ItemData>,
    selectListener: OnSelectListener,
    offsetX: Int = 0,
    offsetY: Int = 0,
    isDialogMode: Boolean = false
): AttachPopupView {
    val builder = PopupBuilder(context ?: author.context)
        .hasShadowBg(false)
        .isDestroyOnDismiss(true)
        .isViewMode(!isDialogMode)
        .navigationBarColor(R.color.deep_bg.res2color())
        .isLightNavigationBar(isNightMode())
        .hasNavigationBar(true)
        .isLightStatusBar(isNightMode())
        .offsetX(offsetX)
        .offsetY(offsetY)
        .atView(author)

    return PopupListView(context ?: author.context, items).apply {
        setOnSelectListener(selectListener)
        popupInfo = builder.popupInfo
    }
}

fun networkPopupMenu(
    author: View,
    context: Context? = null,
    items: List<NetworkPopupListView.ItemData>,
    selectListener: OnSelectListener,
    offsetX: Int = 0,
    offsetY: Int = 0,
    isDialogMode: Boolean = false
): AttachPopupView {
    val builder = PopupBuilder(context ?: author.context)
        .hasShadowBg(false)
        .isDestroyOnDismiss(true)
        .isViewMode(!isDialogMode)
        .navigationBarColor(R.color.deep_bg.res2color())
        .isLightNavigationBar(isNightMode())
        .hasNavigationBar(true)
        .isLightStatusBar(isNightMode())
        .offsetX(offsetX)
        .offsetY(offsetY)
        .atView(author)

    return NetworkPopupListView(context ?: author.context, items).apply {
        setOnSelectListener(selectListener)
        popupInfo = builder.popupInfo
    }
}