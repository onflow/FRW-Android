package com.flowfoundation.wallet.page.address.presenter

import android.view.View
import android.widget.TextView
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.page.address.model.AddressBookCharModel

class AddressBookCharPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<AddressBookCharModel> {

    override fun bind(model: AddressBookCharModel) {
        (view as? TextView)?.text = model.text
    }
}