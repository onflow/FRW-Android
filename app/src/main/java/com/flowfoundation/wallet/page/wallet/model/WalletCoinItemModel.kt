package com.flowfoundation.wallet.page.wallet.model

import com.flowfoundation.wallet.manager.coin.FlowCoin

data class WalletCoinItemModel(
    val coin: FlowCoin,
    val address: String,
    val balance: Float,
    val coinRate: Float,
    val isHideBalance: Boolean = false,
    val currency: String,
    val isStaked: Boolean = false,
    val stakeAmount: Float,
    val quoteChange: Float = 0f,
)