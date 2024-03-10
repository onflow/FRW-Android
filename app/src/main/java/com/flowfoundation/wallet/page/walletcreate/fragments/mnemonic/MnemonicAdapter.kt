package com.flowfoundation.wallet.page.walletcreate.fragments.mnemonic

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.base.recyclerview.getItemView

class MnemonicAdapter : BaseAdapter<MnemonicModel>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return MnemonicItemPresenter(parent.getItemView(R.layout.item_mnemonic))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        (holder as MnemonicItemPresenter).bind(item)
    }
}