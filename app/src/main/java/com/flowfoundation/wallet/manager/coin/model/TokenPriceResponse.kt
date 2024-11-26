package com.flowfoundation.wallet.manager.coin.model

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal


data class TokenPriceResponse(
    @SerializedName("data")
    val data: List<TokenPrice>?,
    @SerializedName("message")
    val message: String?,
    @SerializedName("status")
    val status: Int?
)

data class TokenPrice(
    @SerializedName("contractAddress")
    val contractAddress: String,
    @SerializedName("contractName")
    val contractName: String,
    @SerializedName("rateToFLOW")
    val rateToFLOW: BigDecimal,
    @SerializedName("rateToUSD")
    val rateToUSD: BigDecimal,
    @SerializedName("evmAddress")
    val evmAddress: String?
)
