package com.flowfoundation.wallet.utils

import com.flowfoundation.wallet.R


const val COIN_USD_SYMBOL = "usd"
const val COIN_USD_NAME = "USD"

enum class Coin(val value: Int) {
    FLOW(1),
    FUSD(2),
    USD(1001),
}

private val ICON_MAP by lazy {
    mapOf(
        Coin.FLOW to R.drawable.ic_coin_flow,
        Coin.FUSD to R.drawable.ic_coin_flow,
        Coin.USD to R.drawable.ic_coin_usd,
    )
}