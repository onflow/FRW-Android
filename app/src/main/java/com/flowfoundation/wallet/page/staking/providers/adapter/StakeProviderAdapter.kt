package com.flowfoundation.wallet.page.staking.providers.adapter

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.manager.staking.StakingProvider
import com.flowfoundation.wallet.manager.staking.isLilico
import com.flowfoundation.wallet.page.staking.providers.model.ProviderTitleModel
import com.flowfoundation.wallet.page.staking.providers.presenter.ProviderItemPresenter
import com.flowfoundation.wallet.page.staking.providers.presenter.ProviderRecommendItemPresenter
import com.flowfoundation.wallet.page.staking.providers.presenter.ProviderTitlePresenter

class StakeProviderAdapter : BaseAdapter<Any>(diffCallback) {

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is ProviderTitleModel -> TYPE_TITLE
            else -> if (item is StakingProvider && item.isLilico()) TYPE_PROVIDER_RECOMMEND else TYPE_PROVIDER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_TITLE -> ProviderTitlePresenter(parent.inflate(R.layout.item_stake_provider_title))
            TYPE_PROVIDER -> ProviderItemPresenter(parent.inflate(R.layout.item_stake_provider))
            TYPE_PROVIDER_RECOMMEND -> ProviderRecommendItemPresenter(parent.inflate(R.layout.item_stake_provider_recommend))
            else -> BaseViewHolder(View(parent.context))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ProviderTitlePresenter -> holder.bind(getItem(position) as ProviderTitleModel)
            is ProviderItemPresenter -> holder.bind(getItem(position) as StakingProvider)
            is ProviderRecommendItemPresenter -> holder.bind(getItem(position) as StakingProvider)
        }
    }

    companion object {
        private const val TYPE_TITLE = 1
        private const val TYPE_PROVIDER = 2
        private const val TYPE_PROVIDER_RECOMMEND = 3
    }
}

private val diffCallback = object : DiffUtil.ItemCallback<Any>() {
    override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
        if (oldItem is StakingProvider && newItem is StakingProvider) {
            return oldItem.id == newItem.id
        }
        return oldItem == newItem
    }

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
        if (oldItem is StakingProvider && newItem is StakingProvider) {
            return oldItem == newItem
        }
        return oldItem == newItem
    }
}