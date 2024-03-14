package com.flowfoundation.wallet.page.swap.dialog.select

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.manager.coin.FlowCoin

internal class TokenListAdapter(
    private val selectedCoin: String? = null,
    private var disableCoin: String? = null,
    private val callback: (FlowCoin) -> Unit,
) : BaseAdapter<FlowCoin>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return TokenItemPresenter(parent.inflate(R.layout.item_token_list), selectedCoin, disableCoin, callback)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is TokenItemPresenter -> holder.bind(getItem(position))
        }
    }
}