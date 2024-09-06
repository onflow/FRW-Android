package com.flowfoundation.wallet.page.staking.detail.model

import com.flowfoundation.wallet.manager.staking.StakingNode
import com.flowfoundation.wallet.page.profile.subpage.currency.model.Currency
import com.google.gson.annotations.SerializedName

data class StakingDetailModel(
    @SerializedName("currency")
    var currency: Currency = Currency.USD,
    @SerializedName("balance")
    var balance: Float = 0.0f,
    @SerializedName("coinRate")
    var coinRate: Float = 0.0f,
    @SerializedName("stakingNode")
    var stakingNode: StakingNode = StakingNode(),
)