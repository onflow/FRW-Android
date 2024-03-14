package com.flowfoundation.wallet.page.token.addtoken.model

import com.flowfoundation.wallet.manager.coin.FlowCoin

data class TokenItem(
    val coin: FlowCoin,
    var isAdded: Boolean,
    var isAdding: Boolean? = null,
) {
    fun isNormalState() = !(isAdded || isAdding == true)
}