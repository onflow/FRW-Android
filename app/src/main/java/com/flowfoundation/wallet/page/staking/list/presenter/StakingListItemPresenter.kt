package com.flowfoundation.wallet.page.staking.list.presenter

import android.annotation.SuppressLint
import android.view.View
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemStakeListBinding
import com.flowfoundation.wallet.manager.staking.*
import com.flowfoundation.wallet.page.staking.amount.StakingAmountActivity
import com.flowfoundation.wallet.page.staking.detail.StakingDetailActivity
import com.flowfoundation.wallet.page.staking.list.model.StakingListItemModel
import com.flowfoundation.wallet.utils.formatNum

class StakingListItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<StakingListItemModel> {

    private val binding by lazy { ItemStakeListBinding.bind(view) }

    @SuppressLint("SetTextI18n")
    override fun bind(model: StakingListItemModel) {
        with(binding) {
            claimButton.setOnClickListener {
                StakingAmountActivity.launch(
                    view.context,
                    model.provider
                )
            }
            Glide.with(providerIcon).load(model.provider.icon)
                .placeholder(R.drawable.ic_placeholder).into(providerIcon)
            providerName.text = model.provider.name
            providerRate.text = (model.provider.rate() * 100).formatNum(2) + "%"
            amountView.text = model.stakingNode.stakingCount().formatNum(3)
            rewardView.text = model.stakingNode.tokensRewarded.formatNum(3)
        }

        view.setOnClickListener { StakingDetailActivity.launch(view.context, model.provider) }
    }

}