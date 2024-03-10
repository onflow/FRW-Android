package com.flowfoundation.wallet.page.staking.detail.model

import com.flowfoundation.wallet.manager.staking.StakingNode
import com.flowfoundation.wallet.page.profile.subpage.currency.model.Currency

data class StakingDetailModel(
    var currency: Currency = Currency.USD,
    var balance: Float = 0.0f,
    var coinRate: Float = 0.0f,
    var stakingNode: StakingNode = StakingNode(),
)