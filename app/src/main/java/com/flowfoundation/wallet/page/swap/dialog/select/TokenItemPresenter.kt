package com.flowfoundation.wallet.page.swap.dialog.select

import android.view.View
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemTokenListBinding
import com.flowfoundation.wallet.manager.account.BalanceManager
import com.flowfoundation.wallet.manager.coin.CoinRateManager
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.formatLargeBalanceNumber
import com.flowfoundation.wallet.utils.formatPrice
import java.math.BigDecimal

class TokenItemPresenter(
    private val view: View,
    private val selectedCoin: String? = null,
    private var disableCoin: String? = null,
    private val callback: (FlowCoin) -> Unit
) : BaseViewHolder(view), BasePresenter<FlowCoin> {

    private val binding by lazy { ItemTokenListBinding.bind(view) }

    override fun bind(model: FlowCoin) {
        with(binding) {
            nameView.text = model.name
            symbolView.text = model.symbol.uppercase()
            Glide.with(iconView).load(model.icon()).into(iconView)
            stateButton.setVisible(false)
            view.isSelected = model.isSameCoin(selectedCoin.orEmpty())

            // Get balance and rate
            val balance = BalanceManager.getBalanceList()
                .firstOrNull { it.isSameCoin(model) }?.balance ?: BigDecimal.ZERO
            val rate = CoinRateManager.coinRate(model.contractId()) ?: BigDecimal.ZERO

            tokenAmount.text = "${balance.formatLargeBalanceNumber(isAbbreviation = true)} ${model.symbol.uppercase()}"
            tokenPrice.text = (balance * rate).formatPrice(includeSymbol = true, isAbbreviation = true)
        }

        view.setOnClickListener {
            if (model.isSameCoin(disableCoin.orEmpty()).not()) {
                callback.invoke(model)
            }
        }
    }
}