package com.flowfoundation.wallet.page.staking.providers.presenter

import android.annotation.SuppressLint
import android.view.View
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemStakeProviderBinding
import com.flowfoundation.wallet.manager.staking.STAKING_DEFAULT_NORMAL_APY
import com.flowfoundation.wallet.manager.staking.StakingManager
import com.flowfoundation.wallet.manager.staking.StakingProvider
import com.flowfoundation.wallet.manager.staking.isLilico
import com.flowfoundation.wallet.page.staking.amount.StakingAmountActivity
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.formatNum

class ProviderItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<StakingProvider> {

    private val binding by lazy { ItemStakeProviderBinding.bind(view) }

    @SuppressLint("SetTextI18n")
    override fun bind(model: StakingProvider) {
        with(binding) {
            Glide.with(iconView).load(model.icon).placeholder(R.drawable.ic_placeholder).into(iconView)
            titleView.text = model.name
            descView.text = model.description
            rateTitle.text = (if (model.isLilico()) (StakingManager.apy() * 100).formatNum(2) else "${STAKING_DEFAULT_NORMAL_APY * 100}") + "%"
            rateDesc.text = R.string.stake.res2String()
        }

        view.setOnClickListener { StakingAmountActivity.launch(view.context, model) }
    }

}