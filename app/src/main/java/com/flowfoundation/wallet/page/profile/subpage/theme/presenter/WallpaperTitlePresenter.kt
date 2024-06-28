package com.flowfoundation.wallet.page.profile.subpage.theme.presenter

import android.view.View
import android.widget.TextView
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder


class WallpaperTitlePresenter(private val view: View): BaseViewHolder(view), BasePresenter<String> {

    override fun bind(model: String) {
        (view as TextView).text = model
    }
}