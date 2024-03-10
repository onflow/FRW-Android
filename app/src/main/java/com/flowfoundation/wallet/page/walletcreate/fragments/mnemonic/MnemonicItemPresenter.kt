package com.flowfoundation.wallet.page.walletcreate.fragments.mnemonic

import android.view.View
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.widgets.MnemonicItem

class MnemonicItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<MnemonicModel> {

    override fun bind(model: MnemonicModel) {
        (view as MnemonicItem).setText(model)
    }
}