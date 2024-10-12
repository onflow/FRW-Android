package com.flowfoundation.wallet.page.walletcreate.fragments.mnemonic

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.base.recyclerview.getItemView

class MnemonicWhiteSerialAdapter : BaseAdapter<MnemonicModel>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return MnemonicWhiteSerialItemPresenter(parent.getItemView(R.layout.item_mnemonic_white_serial))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        (holder as MnemonicWhiteSerialItemPresenter).bind(item)
    }
}