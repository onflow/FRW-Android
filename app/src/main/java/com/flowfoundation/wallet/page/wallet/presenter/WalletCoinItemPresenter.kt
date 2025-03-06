package com.flowfoundation.wallet.page.wallet.presenter

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.View
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.LayoutWalletCoinItemBinding
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.page.profile.subpage.wallet.ChildAccountCollectionManager
import com.flowfoundation.wallet.page.token.detail.TokenDetailActivity
import com.flowfoundation.wallet.page.wallet.model.WalletCoinItemModel
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.formatLargeBalanceNumber
import com.flowfoundation.wallet.utils.formatNum
import com.flowfoundation.wallet.utils.formatPrice
import java.math.BigDecimal
import kotlin.math.absoluteValue

class WalletCoinItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<WalletCoinItemModel> {

    private val binding by lazy { LayoutWalletCoinItemBinding.bind(view) }

    @SuppressLint("SetTextI18n")
    override fun bind(model: WalletCoinItemModel) {
        with(binding) {
            Glide.with(coinIcon).load(model.coin.icon()).into(coinIcon)
            coinName.text = model.coin.name
            if (model.isHideBalance) {
                coinBalance.text = "**** ${model.coin.symbol.uppercase()}"
                coinBalancePrice.text = "****"
            } else {
                coinBalance.text =
                    "${model.balance.formatLargeBalanceNumber(isAbbreviation = true)} ${model.coin.symbol.uppercase()}"
                coinBalancePrice.text =
                    (model.balance * model.coinRate).formatPrice(includeSymbol = true, isAbbreviation = true)
            }
            coinPrice.text = if (model.coinRate == BigDecimal.ZERO) "" else model.coinRate.formatPrice(includeSymbol = true)
            val isStable = model.quoteChange == 0f
            val isRise = model.quoteChange > 0
            tvQuoteChange.backgroundTintList =
                ColorStateList.valueOf(
                    if (isRise) R.color.accent_quote_up_opacity.res2color() else R.color.accent_quote_down_opacity.res2color()
                )
            tvQuoteChange.setTextColor(
                if (isRise) R.color.accent_green.res2color() else R.color.accent_red.res2color()
            )
            tvQuoteChange.text = if (isStable) {
                ""
            } else {
                (if (isRise) "+" else "-") + "${model.quoteChange.absoluteValue.formatNum(2)}%"
            }
            bindStaking(model)
            bindAccessible(model.coin)
            view.setOnClickListener { TokenDetailActivity.launch(view.context, model.coin) }
        }
    }

    private fun bindStaking(model: WalletCoinItemModel) {
        if (!model.coin.isFlowCoin() || !model.isStaked) {
            setStakingVisible(false)
            return
        }
        setStakingVisible(true)
        binding.stakingAmount.text =
            view.context.getString(R.string.flow_num, model.stakeAmount.formatLargeBalanceNumber(isAbbreviation = true))
        binding.stakingAmountPrice.text =
            (model.stakeAmount * model.coinRate.toFloat()).formatPrice(includeSymbol = true, isAbbreviation = true)
    }

    private fun bindAccessible(coin: FlowCoin) {
        val accessible =
            ChildAccountCollectionManager.isTokenAccessible(coin.contractName(), coin.address)
        if (accessible.not()) {
            setStakingVisible(false)
        }
        binding.coinPrice.setVisible(accessible)
        binding.tvQuoteChange.setVisible(accessible)
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