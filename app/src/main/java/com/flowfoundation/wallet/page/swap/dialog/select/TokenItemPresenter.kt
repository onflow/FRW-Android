package com.flowfoundation.wallet.page.swap.dialog.select

import android.view.View
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemTokenListBinding
import com.flowfoundation.wallet.manager.coin.CoinRateManager
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.OnCoinRateUpdate
import com.flowfoundation.wallet.page.token.detail.model.MoveToken
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.formatLargeBalanceNumber
import com.flowfoundation.wallet.utils.formatPrice
import com.flowfoundation.wallet.utils.logd
import java.math.BigDecimal

class TokenItemPresenter(
    private val view: View,
    private val selectedCoin: String? = null,
    private val disableCoin: String? = null,
    private val callback: (MoveToken) -> Unit
) : BaseViewHolder(view), BasePresenter<MoveToken> {

    private val binding by lazy { ItemTokenListBinding.bind(view) }

    override fun bind(model: MoveToken) {
        with(binding) {
            nameView.text = model.tokenInfo.name
            symbolView.text = model.tokenInfo.symbol.uppercase()
            Glide.with(iconView).load(model.tokenInfo.icon()).into(iconView)
            stateButton.setVisible(false)
            view.isSelected = model.tokenInfo.isSameCoin(selectedCoin.orEmpty())
            
            val balance = model.tokenBalance
            val contractId = model.tokenInfo.contractId()
            
            // Set the balance text
            tokenAmount.text = "${balance.formatLargeBalanceNumber(isAbbreviation = true)} ${model.tokenInfo.symbol.uppercase()}"
            
            // Get current rate and update price if available
            val rate = CoinRateManager.coinRate(contractId)
            
            if (rate != null && rate > BigDecimal.ZERO) {
                val dollarValue = balance * rate
                tokenPrice.text = dollarValue.formatPrice(includeSymbol = true, isAbbreviation = true)
                tokenPrice.setVisible(true)
            } else {
                // If it's USDC or USDC.e, show 1:1 rate
                if (model.tokenInfo.symbol.contains("USDC", ignoreCase = true)) {
                    val dollarValue = balance
                    tokenPrice.text = dollarValue.formatPrice(includeSymbol = true, isAbbreviation = true)
                    tokenPrice.setVisible(true)
                } else if (model.tokenInfo.symbol.equals("WFLOW", ignoreCase = true) || 
                         model.tokenInfo.symbol.equals("stFlow", ignoreCase = true)) {
                    // For WFLOW and stFlow, try to use FLOW rate
                    val flowRate = CoinRateManager.coinRate("A.1654653399040a61.FlowToken")
                    if (flowRate != null && flowRate > BigDecimal.ZERO) {
                        val dollarValue = balance * flowRate
                        tokenPrice.text = dollarValue.formatPrice(includeSymbol = true, isAbbreviation = true)
                        tokenPrice.setVisible(true)
                    } else {
                        tokenPrice.setVisible(false)
                    }
                } else {
                    tokenPrice.setVisible(false)
                }
            }
        }

        view.setOnClickListener {
            if (model.tokenInfo.isSameCoin(disableCoin.orEmpty()).not()) {
                callback.invoke(model)
            }
        }
    }
}