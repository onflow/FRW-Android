package com.flowfoundation.wallet.page.staking.list.model

import com.flowfoundation.wallet.manager.staking.StakingNode
import com.flowfoundation.wallet.manager.staking.StakingProvider

data class StakingListItemModel(
    val provider: StakingProvider,
    val stakingNode: StakingNode,
)