package com.flowfoundation.wallet.page.swap.model

import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.flowfoundation.wallet.network.model.SwapEstimateResponse
import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class SwapModel(
    @SerializedName("fromCoin")
    val fromCoin: FungibleToken? = null,
    @SerializedName("toCoin")
    val toCoin: FungibleToken? = null,
    @SerializedName("onBalanceUpdate")
    val onBalanceUpdate: Boolean? = null,
    @SerializedName("onCoinRateUpdate")
    val onCoinRateUpdate: Boolean? = null,
    @SerializedName("onEstimateFromUpdate")
    val onEstimateFromUpdate: BigDecimal? = null,
    @SerializedName("onEstimateToUpdate")
    val onEstimateToUpdate: BigDecimal? = null,
    @SerializedName("onEstimateLoading")
    val onEstimateLoading: Boolean? = null,
    @SerializedName("estimateData")
    val estimateData: SwapEstimateResponse.Data? = null,
)