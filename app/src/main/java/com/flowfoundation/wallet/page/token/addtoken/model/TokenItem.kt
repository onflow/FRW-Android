package com.flowfoundation.wallet.page.token.addtoken.model

import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.google.gson.annotations.SerializedName

data class TokenItem(
    @SerializedName("coin")
    val coin: FlowCoin,
    @SerializedName("isAdded")
    var isAdded: Boolean,
    @SerializedName("isAdding")
    var isAdding: Boolean? = null,
) {
    fun isNormalState() = !(isAdded || isAdding == true)
}