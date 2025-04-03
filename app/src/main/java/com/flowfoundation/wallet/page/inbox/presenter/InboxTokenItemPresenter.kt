package com.flowfoundation.wallet.page.inbox.presenter

import android.annotation.SuppressLint
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemInboxTokenBinding
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.network.model.InboxToken
import com.flowfoundation.wallet.page.inbox.InboxViewModel
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.utils.format
import com.flowfoundation.wallet.utils.formatPrice

class InboxTokenItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<InboxToken> {
    private val binding by lazy { ItemInboxTokenBinding.bind(view) }

    private val viewModel by lazy { ViewModelProvider(findActivity(view) as FragmentActivity)[InboxViewModel::class.java] }

    @SuppressLint("SetTextI18n")
    override fun bind(model: InboxToken) {
        with(binding) {
            val coin = FlowCoinListManager.coinList().firstOrNull { it.address == model.coinAddress } ?: return
            Glide.with(coinIconView).load(coin.icon()).into(coinIconView)
            amountView.text = "${model.amount.format()} ${coin.symbol.uppercase()}"

            val marketValue = model.marketValue
            priceCountView.text = marketValue?.formatPrice(includeSymbol = true, includeSymbolSpace = true) ?: "$0"

            claimButton.setOnClickListener { viewModel.claimToken(model) }
        }
    }
}