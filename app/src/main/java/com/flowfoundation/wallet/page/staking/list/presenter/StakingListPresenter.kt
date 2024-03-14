package com.flowfoundation.wallet.page.staking.list.presenter

import android.graphics.Color
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowfoundation.wallet.databinding.ActivityStakeListBinding
import com.flowfoundation.wallet.page.staking.list.adapter.StakeListAdapter
import com.flowfoundation.wallet.page.staking.providers.StakingProviderActivity
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration

class StakingListPresenter(
    private val binding: ActivityStakeListBinding,
) {

    private val adapter = StakeListAdapter()

    init {
        with(binding.recyclerView) {
            adapter = this@StakingListPresenter.adapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            addItemDecoration(
                ColorDividerItemDecoration(Color.TRANSPARENT, 12.dp2px().toInt())
            )
        }
        binding.button.setOnClickListener { StakingProviderActivity.launch(binding.root.context) }
    }

    fun bind(data: List<Any>) {
        adapter.setNewDiffData(data)
    }
}