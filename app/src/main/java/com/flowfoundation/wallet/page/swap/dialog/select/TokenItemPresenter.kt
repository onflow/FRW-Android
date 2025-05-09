package com.flowfoundation.wallet.page.swap.dialog.select

import android.annotation.SuppressLint
import android.view.View
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemTokenListBinding
import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.formatLargeBalanceNumber
import com.flowfoundation.wallet.utils.formatPrice

class TokenItemPresenter(
    private val view: View,
    private val selectedCoin: String? = null,
    private val disableCoin: String? = null,
    private val callback: (FungibleToken) -> Unit
) : BaseViewHolder(view), BasePresenter<FungibleToken> {

    private val binding by lazy { ItemTokenListBinding.bind(view) }

    @SuppressLint("SetTextI18n")
    override fun bind(model: FungibleToken) {
        with(binding) {
            nameView.text = model.name
            symbolView.text = model.symbol.uppercase()
            Glide.with(iconView).load(model.tokenIcon()).into(iconView)
            stateButton.setVisible(false)
            view.isSelected = model.isSameToken(selectedCoin.orEmpty())
            
            // Display balance
            tokenAmount.text = "${model.tokenBalance().formatLargeBalanceNumber(isAbbreviation = true)} ${model.symbol.uppercase()}"

            tokenPrice.text = model.tokenBalancePrice().formatPrice(includeSymbol = true, isAbbreviation = true)
            tokenPrice.setVisible(true)
        }

        view.setOnClickListener {
            if (model.isSameToken(disableCoin.orEmpty()).not()) {
                callback.invoke(model)
            }
        }
    }
}