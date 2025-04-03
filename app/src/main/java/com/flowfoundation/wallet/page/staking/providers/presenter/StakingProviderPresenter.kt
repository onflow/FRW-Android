package com.flowfoundation.wallet.page.staking.providers.presenter

import android.graphics.Color
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowfoundation.wallet.databinding.ActivityStakeProviderBinding
import com.flowfoundation.wallet.page.staking.providers.adapter.StakeProviderAdapter
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration

class StakingProviderPresenter(
    binding: ActivityStakeProviderBinding,
) {

    private val adapter = StakeProviderAdapter()

    init {
        with(binding.recyclerView) {
            adapter = this@StakingProviderPresenter.adapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            addItemDecoration(
                ColorDividerItemDecoration(Color.TRANSPARENT, 8.dp2px().toInt())
            )
        }
    }

    fun bind(data: List<Any>) {
        adapter.setNewDiffData(data)
    }
}