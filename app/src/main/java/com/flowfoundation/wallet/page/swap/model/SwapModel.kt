package com.flowfoundation.wallet.page.swap.model

import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.network.model.SwapEstimateResponse

data class SwapModel(
    val fromCoin: FlowCoin? = null,
    val toCoin: FlowCoin? = null,
    val onBalanceUpdate: Boolean? = null,
    val onCoinRateUpdate: Boolean? = null,
    val onEstimateFromUpdate: Float? = null,
    val onEstimateToUpdate: Float? = null,
    val onEstimateLoading: Boolean? = null,
    val estimateData: SwapEstimateResponse.Data? = null,
)