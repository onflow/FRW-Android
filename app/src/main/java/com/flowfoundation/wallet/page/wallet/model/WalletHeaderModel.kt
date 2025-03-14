package com.flowfoundation.wallet.page.wallet.model

import com.flowfoundation.wallet.network.model.WalletListData
import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class WalletHeaderModel(
    @SerializedName("walletList")
    val walletList: WalletListData,
    @SerializedName("balance")
    var balance: BigDecimal,
    @SerializedName("coinCount")
    var coinCount: Int = 0
)