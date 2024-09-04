package com.flowfoundation.wallet.page.wallet.model

import com.flowfoundation.wallet.network.model.WalletListData
import com.google.gson.annotations.SerializedName

data class WalletHeaderModel(
    @SerializedName("walletList")
    val walletList: WalletListData,
    @SerializedName("balance")
    var balance: Float,
    @SerializedName("coinCount")
    var coinCount: Int = 0,
    @SerializedName("transactionCount")
    var transactionCount: Int? = 0,
)