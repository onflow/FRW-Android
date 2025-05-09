package com.flowfoundation.wallet.page.wallet.model

import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.google.gson.annotations.SerializedName

data class WalletCoinItemModel(
    @SerializedName("token")
    val token: FungibleToken,
    @SerializedName("isHideBalance")
    val isHideBalance: Boolean = false,
    @SerializedName("isStaked")
    val isStaked: Boolean = false,
    @SerializedName("stakeAmount")
    val stakeAmount: Float
)