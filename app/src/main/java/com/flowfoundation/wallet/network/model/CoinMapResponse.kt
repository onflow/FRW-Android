package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName

data class CoinMapResponse(
    @SerializedName("data")
    val data: CoinMapDataWrapper,

    @SerializedName("message")
    val message: String,

    @SerializedName("status")
    val status: Int,
)

data class CoinMapDataWrapper(
    @SerializedName("data")
    val data: List<CoinMapData>,
)

data class CoinMapData(
    @SerializedName("first_historical_data")
    val firstHistoricalData: String,

    @SerializedName("id")
    val id: Int,

    @SerializedName("is_active")
    val isActive: Int,

    @SerializedName("last_historical_data")
    val lastHistoricalData: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("platform")
    val platform: CoinPlatform?,

    @SerializedName("rank")
    val rank: Int,

    @SerializedName("slug")
    val slug: String,

    @SerializedName("symbol")
    val symbol: String,
)

data class CoinPlatform(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("slug")
    val slug: String,
    @SerializedName("symbol")
    val symbol: String,
    @SerializedName("token_address")
    val tokenAddress: String,
)