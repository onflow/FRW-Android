package com.flowfoundation.wallet.page.wallet.presenter

import android.annotation.SuppressLint
import android.view.View
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.LayoutWalletCoinItemBinding
import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.flowfoundation.wallet.page.profile.subpage.wallet.ChildAccountCollectionManager
import com.flowfoundation.wallet.page.token.detail.TokenDetailActivity
import com.flowfoundation.wallet.page.wallet.model.WalletCoinItemModel
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.formatLargeBalanceNumber
import com.flowfoundation.wallet.utils.formatPrice
import java.math.BigDecimal

class WalletCoinItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<WalletCoinItemModel> {

    private val binding by lazy { LayoutWalletCoinItemBinding.bind(view) }

    @SuppressLint("SetTextI18n")
    override fun bind(model: WalletCoinItemModel) {
        with(binding) {
            Glide.with(coinIcon).load(model.token.tokenIcon()).into(coinIcon)
            coinName.text = model.token.name
            if (model.isHideBalance) {
                coinBalance.text = "**** ${model.token.symbol.uppercase()}"
                coinBalancePrice.text = "****"
            } else {
                coinBalance.text =
                    "${model.token.tokenBalance().formatLargeBalanceNumber(true)} ${model.token.symbol.uppercase()}"
                coinBalancePrice.text = (model.token.tokenBalancePrice()).formatPrice(includeSymbol = true, isAbbreviation = true)
            }
            coinPrice.text = if (model.token.tokenPrice() == BigDecimal.ZERO) "" else model.token.tokenPrice().formatPrice(includeSymbol = true)
            ivVerified.setVisible(model.token.isVerified)
            tvQuoteChange.gone()
            bindStaking(model)
            bindAccessible(model.token)
            view.setOnClickListener { TokenDetailActivity.launch(view.context, model.token.contractId()) }
        }
    }

    private fun bindStaking(model: WalletCoinItemModel) {
        if (!model.token.isFlowToken() || !model.isStaked) {
            setStakingVisible(false)
            return
        }
        setStakingVisible(true)
        binding.stakingAmount.text =
            view.context.getString(R.string.flow_num, model.stakeAmount.formatLargeBalanceNumber(isAbbreviation = true))
        binding.stakingAmountPrice.text =
            (model.stakeAmount * model.token.tokenPrice().toFloat()).formatPrice(includeSymbol = true, isAbbreviation = true)
    }

    private fun bindAccessible(token: FungibleToken) {
        val accessible =
            ChildAccountCollectionManager.isTokenAccessible(token.tokenContractName(), token.tokenAddress())
        if (accessible.not()) {
            setStakingVisible(false)
        }
        binding.coinPrice.setVisible(accessible)
        binding.tvInaccessibleTag.setVisible(accessible.not())
    }

    private fun setStakingVisible(isVisible: Boolean) {
        with(binding) {
            stakingLine.setVisible(isVisible)
            stakingAmount.setVisible(isVisible)
            stakingName.setVisible(isVisible)
            stakingPoint.setVisible(isVisible)
            stakingAmountPrice.setVisible(isVisible)
        }
    }
}