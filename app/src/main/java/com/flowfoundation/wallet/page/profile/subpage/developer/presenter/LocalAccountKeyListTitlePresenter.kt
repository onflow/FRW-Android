package com.flowfoundation.wallet.page.profile.subpage.developer.presenter

import android.view.View
import android.widget.TextView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.LayoutLocalKeyItemBinding
import com.flowfoundation.wallet.page.profile.subpage.developer.model.LocalAccountKey
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.textToClipboard
import com.flowfoundation.wallet.utils.toast


class LocalAccountKeyListTitlePresenter(private val view: View) : BaseViewHolder(view), BasePresenter<Int> {

    override fun bind(model: Int) {
        (view as TextView).text = model.res2String()
    }

}