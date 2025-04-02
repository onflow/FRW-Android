package com.flowfoundation.wallet.page.swap.dialog.select

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.page.token.detail.model.MoveToken

class TokenListAdapter(
    private var selectedCoin: String? = null,
    private val disableCoin: String? = null,
    private val callback: (MoveToken) -> Unit
) : BaseAdapter<MoveToken>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return TokenItemPresenter(
            LayoutInflater.from(parent.context).inflate(R.layout.item_token_list, parent, false),
            selectedCoin,
            disableCoin,
            callback
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is TokenItemPresenter -> holder.bind(getItem(position))
        }
    }

    fun updateSelectedCoin(selectedCoin: String?) {
        this.selectedCoin = selectedCoin
        notifyDataSetChanged()
    }
}