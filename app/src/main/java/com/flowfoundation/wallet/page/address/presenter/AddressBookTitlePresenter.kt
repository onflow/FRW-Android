package com.flowfoundation.wallet.page.address.presenter

import android.view.View
import android.widget.TextView
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.page.address.model.AddressBookTitleModel

class AddressBookTitlePresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<AddressBookTitleModel> {

    override fun bind(model: AddressBookTitleModel) {
        (view as? TextView)?.text = model.title
    }
}