package io.outblock.lilico.page.wallet.presenter

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.View
import com.bumptech.glide.Glide
import io.outblock.lilico.R
import io.outblock.lilico.base.presenter.BasePresenter
import io.outblock.lilico.base.recyclerview.BaseViewHolder
import io.outblock.lilico.databinding.LayoutWalletCoinItemBinding
import io.outblock.lilico.manager.coin.FlowCoin
import io.outblock.lilico.page.profile.subpage.wallet.ChildAccountCollectionManager
import io.outblock.lilico.page.token.detail.TokenDetailActivity
import io.outblock.lilico.page.wallet.model.WalletCoinItemModel
import io.outblock.lilico.utils.extensions.res2color
import io.outblock.lilico.utils.extensions.setVisible
import io.outblock.lilico.utils.formatNum
import io.outblock.lilico.utils.formatPrice
import java.math.RoundingMode
import kotlin.math.absoluteValue

class WalletCoinItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<WalletCoinItemModel> {

    private val binding by lazy { LayoutWalletCoinItemBinding.bind(view) }

    @SuppressLint("SetTextI18n")
    override fun bind(model: WalletCoinItemModel) {
        with(binding) {
            Glide.with(coinIcon).load(model.coin.icon).into(coinIcon)
            coinName.text = model.coin.name
            if (model.isHideBalance) {
                coinBalance.text = "**** ${model.coin.symbol.uppercase()}"
                coinBalancePrice.text = "****"
            } else {
                coinBalance.text = "${model.balance.formatNum(roundingMode = RoundingMode.HALF_UP)} ${model.coin.symbol.uppercase()}"
                coinBalancePrice.text = (model.balance * model.coinRate).formatPrice(includeSymbol = true)
            }
            coinPrice.text = model.coinRate.formatPrice(includeSymbol = true)
            val isRise = model.quoteChange >= 0
            tvQuoteChange.backgroundTintList =
                ColorStateList.valueOf(if (isRise) R.color.accent_quote_up_opacity.res2color() else R.color.accent_quote_down_opacity.res2color())
            tvQuoteChange.setTextColor(if (isRise) R.color.accent_green.res2color() else R.color.accent_red.res2color())
            tvQuoteChange.text = (if(isRise) "+" else "-") + "${model.quoteChange.absoluteValue.formatNum(2)}%"
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
        binding.stakingAmount.text = view.context.getString(R.string.flow_num, model.stakeAmount.formatNum(3))
        binding.stakingAmountPrice.text = (model.stakeAmount * model.coinRate).formatPrice(includeSymbol = true)
    }

    private fun bindAccessible(coin: FlowCoin) {
        val accessible = ChildAccountCollectionManager.isTokenAccessible(coin.contractName, coin.address())
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