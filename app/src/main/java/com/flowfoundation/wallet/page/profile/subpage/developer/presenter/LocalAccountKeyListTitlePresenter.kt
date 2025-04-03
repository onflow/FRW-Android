package com.flowfoundation.wallet.page.profile.subpage.developer.presenter

import android.view.View
import android.widget.TextView
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.utils.extensions.res2String

class LocalAccountKeyListTitlePresenter(private val view: View) : BaseViewHolder(view), BasePresenter<Int> {

    override fun bind(model: Int) {
        (view as TextView).text = model.res2String()
    }

}