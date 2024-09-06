package com.flowfoundation.wallet.page.swap.model

import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.network.model.SwapEstimateResponse
import com.google.gson.annotations.SerializedName

data class SwapModel(
    @SerializedName("fromCoin")
    val fromCoin: FlowCoin? = null,
    @SerializedName("toCoin")
    val toCoin: FlowCoin? = null,
    @SerializedName("onBalanceUpdate")
    val onBalanceUpdate: Boolean? = null,
    @SerializedName("onCoinRateUpdate")
    val onCoinRateUpdate: Boolean? = null,
    @SerializedName("onEstimateFromUpdate")
    val onEstimateFromUpdate: Float? = null,
    @SerializedName("onEstimateToUpdate")
    val onEstimateToUpdate: Float? = null,
    @SerializedName("onEstimateLoading")
    val onEstimateLoading: Boolean? = null,
    @SerializedName("estimateData")
    val estimateData: SwapEstimateResponse.Data? = null,
)