package com.flowfoundation.wallet.page.wallet.adapter

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.page.wallet.model.WalletCoinItemModel
import com.flowfoundation.wallet.page.wallet.model.WalletHeaderModel
import com.flowfoundation.wallet.page.wallet.presenter.WalletCoinItemPresenter
import com.flowfoundation.wallet.page.wallet.presenter.WalletHeaderPresenter
import com.flowfoundation.wallet.page.wallet.walletListDiffCallback

class WalletFragmentAdapter : BaseAdapter<Any>(walletListDiffCallback) {
    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
//            is WalletHeaderModel -> TYPE_WALLET_HEADER
            is WalletCoinItemModel -> TYPE_WALLET_COIN_ITEM
            else -> TYPE_WALLET_COIN_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
//            TYPE_WALLET_HEADER -> WalletHeaderPresenter(parent.inflate(R.layout.layout_wallet_header))
            TYPE_WALLET_COIN_ITEM -> WalletCoinItemPresenter(parent.inflate(R.layout.layout_wallet_coin_item))
            else -> BaseViewHolder(View(parent.context))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is WalletHeaderPresenter -> holder.bind(getItem(position) as WalletHeaderModel)
            is WalletCoinItemPresenter -> holder.bind(getItem(position) as WalletCoinItemModel)
        }
    }

    companion object {
        private const val TYPE_WALLET_COIN_ITEM = 2
    }
}