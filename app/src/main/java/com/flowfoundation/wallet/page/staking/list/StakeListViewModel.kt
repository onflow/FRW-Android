package com.flowfoundation.wallet.page.staking.list

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.staking.StakingManager
import com.flowfoundation.wallet.page.staking.list.model.StakingListItemModel
import com.flowfoundation.wallet.utils.ioScope

class StakeListViewModel : ViewModel() {

    val data = MutableLiveData<List<StakingListItemModel>>()

    fun load() {
        ioScope {
            data.postValue(StakingManager.stakingInfo().nodes.mapNotNull { node ->
                StakingManager.providers().firstOrNull { it.id == node.nodeID }?.let {
                    StakingListItemModel(
                        provider = it,
                        stakingNode = node,
                    )
                }
            })
        }
    }
}