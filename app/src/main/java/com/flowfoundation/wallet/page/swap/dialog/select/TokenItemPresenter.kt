package com.flowfoundation.wallet.page.swap.dialog.select

import android.view.View
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemTokenListBinding
import com.flowfoundation.wallet.manager.coin.CoinRateManager
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.page.token.detail.provider.EVMAccountTokenProvider
import com.flowfoundation.wallet.page.token.detail.provider.FlowAccountTokenProvider
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.formatLargeBalanceNumber
import com.flowfoundation.wallet.utils.formatPrice
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import java.math.BigDecimal

class TokenItemPresenter(
    private val view: View,
    private val selectedCoin: String? = null,
    private var disableCoin: String? = null,
    private val fromAddress: String,
    private val callback: (FlowCoin) -> Unit
) : BaseViewHolder(view), BasePresenter<FlowCoin> {

    private val binding by lazy { ItemTokenListBinding.bind(view) }
    private var currentCoin: FlowCoin? = null
    private val provider by lazy {
        if (EVMWalletManager.isEVMWalletAddress(fromAddress)) {
            EVMAccountTokenProvider()
        } else {
            FlowAccountTokenProvider()
        }
    }

    override fun bind(model: FlowCoin) {
        with(binding) {
            nameView.text = model.name
            symbolView.text = model.symbol.uppercase()
            Glide.with(iconView).load(model.icon()).into(iconView)
            stateButton.setVisible(false)
            view.isSelected = model.isSameCoin(selectedCoin.orEmpty())

            currentCoin = model
            
            // Get token balance using MoveTokenProvider
            ioScope {
                val moveTokens = provider.getMoveTokenList(fromAddress)
                val moveToken = moveTokens.find { it.tokenInfo.contractId() == model.contractId() }
                
                val balance = moveToken?.tokenBalance ?: BigDecimal.ZERO
                val rate = CoinRateManager.coinRate(model.contractId()) ?: BigDecimal.ZERO
                
                uiScope {
                    tokenAmount.text = "${balance.formatLargeBalanceNumber(isAbbreviation = true)} ${model.symbol.uppercase()}"
                    tokenPrice.text = (balance * rate).formatPrice(includeSymbol = true, isAbbreviation = true)
                }
            }
        }

        view.setOnClickListener {
            if (model.isSameCoin(disableCoin.orEmpty()).not()) {
                callback.invoke(model)
            }
        }
    }
}