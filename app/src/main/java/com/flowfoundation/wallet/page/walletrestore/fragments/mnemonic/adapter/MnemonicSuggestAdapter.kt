package com.flowfoundation.wallet.page.walletrestore.fragments.mnemonic.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.page.walletrestore.fragments.mnemonic.presenter.MnemonicSuggestItemPresenter


class MnemonicSuggestAdapter : BaseAdapter<String>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return MnemonicSuggestItemPresenter(parent.inflate(R.layout.item_mnemonic_suggest))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as MnemonicSuggestItemPresenter).bind(getItem(position))
    }
}