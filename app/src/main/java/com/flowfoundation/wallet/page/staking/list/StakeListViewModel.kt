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
            data.postValue(StakingManager.stakingInfo().nodes.map { node ->
                StakingListItemModel(
                    provider = StakingManager.providers().first { it.id == node.nodeID },
                    stakingNode = node,
                )
            })
        }
    }
}