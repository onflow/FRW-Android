package com.flowfoundation.wallet.page.token.manage.presenter

import android.annotation.SuppressLint
import android.view.View
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.LayoutManageTokenItemBinding
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.formatLargeBalanceNumber
import com.flowfoundation.wallet.utils.formatPrice
import java.math.BigDecimal


class ManageTokenItemPresenter(
    private val view: View
) : BaseViewHolder(view), BasePresenter<FungibleToken> {

    private val binding by lazy { LayoutManageTokenItemBinding.bind(view) }

    @SuppressLint("SetTextI18n")
    override fun bind(model: FungibleToken) {
        with(binding) {
            Glide.with(coinIcon).load(model.tokenIcon()).into(coinIcon)
            coinName.text = model.name
            coinBalance.text =
                "${model.tokenBalance().formatLargeBalanceNumber(true)} ${model.symbol.uppercase()}"
            coinBalancePrice.text = (model.tokenBalancePrice()).formatPrice(includeSymbol = true, isAbbreviation = true)
            coinPrice.text = if (model.tokenPrice() == BigDecimal.ZERO) "" else model.tokenPrice().formatPrice(includeSymbol = true)
            ivVerified.setVisible(model.isVerified)

            switchDisplay.setOnCheckedChangeListener(null)
            switchDisplay.isChecked = FungibleTokenListManager.isDisplayToken(contractId = model.contractId())
            switchDisplay.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    FungibleTokenListManager.addDisplayToken(model)
                } else {
                    FungibleTokenListManager.removeDisplayToken(model)
                }
            }
        }
    }

}