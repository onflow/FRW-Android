package com.flowfoundation.wallet.page.swap.dialog.select

import android.view.View
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemTokenListBinding
import com.flowfoundation.wallet.manager.account.Balance
import com.flowfoundation.wallet.manager.account.BalanceManager
import com.flowfoundation.wallet.manager.account.OnBalanceUpdate
import com.flowfoundation.wallet.manager.coin.CoinRateManager
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.formatLargeBalanceNumber
import com.flowfoundation.wallet.utils.formatPrice
import com.flowfoundation.wallet.utils.logd
import java.math.BigDecimal

class TokenItemPresenter(
    private val view: View,
    private val selectedCoin: String? = null,
    private var disableCoin: String? = null,
    private val fromAddress: String,
    private val callback: (FlowCoin) -> Unit
) : BaseViewHolder(view), BasePresenter<FlowCoin>, OnBalanceUpdate {

    private val binding by lazy { ItemTokenListBinding.bind(view) }
    private var currentCoin: FlowCoin? = null

    override fun bind(model: FlowCoin) {
        with(binding) {
            nameView.text = model.name
            symbolView.text = model.symbol.uppercase()
            Glide.with(iconView).load(model.icon()).into(iconView)
            stateButton.setVisible(false)
            view.isSelected = model.isSameCoin(selectedCoin.orEmpty())

            currentCoin = model
            BalanceManager.addListener(this@TokenItemPresenter)
            
            // Trigger balance refresh for the token and address
            BalanceManager.getBalanceByCoin(model, fromAddress)
        }

        view.setOnClickListener {
            if (model.isSameCoin(disableCoin.orEmpty()).not()) {
                callback.invoke(model)
            }
        }
    }

    override fun onBalanceUpdate(coin: FlowCoin, balance: Balance) {
        if (currentCoin?.isSameCoin(coin.contractId()) == true) {
            with(binding) {
                val rate = CoinRateManager.coinRate(coin.contractId()) ?: BigDecimal.ZERO
                tokenAmount.text = "${balance.balance.formatLargeBalanceNumber(isAbbreviation = true)} ${coin.symbol.uppercase()}"
                tokenPrice.text = (balance.balance * rate).formatPrice(includeSymbol = true, isAbbreviation = true)
            }
        }
    }
}