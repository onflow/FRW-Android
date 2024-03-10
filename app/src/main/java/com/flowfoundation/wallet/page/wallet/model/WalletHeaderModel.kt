package com.flowfoundation.wallet.page.wallet.model

import com.flowfoundation.wallet.network.model.WalletListData

data class WalletHeaderModel(
    val walletList: WalletListData,
    var balance: Float,
    var coinCount: Int = 0,
    var transactionCount: Int? = 0,
)