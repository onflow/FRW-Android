package com.flowfoundation.wallet.page.staking.list.model

import com.flowfoundation.wallet.manager.staking.StakingNode
import com.flowfoundation.wallet.manager.staking.StakingProvider
import com.google.gson.annotations.SerializedName

data class StakingListItemModel(
    @SerializedName("provider")
    val provider: StakingProvider,
    @SerializedName("stakingNode")
    val stakingNode: StakingNode,
)