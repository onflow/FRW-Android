package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.utils.gmtToTs

data class CoinRateResponse(
    @SerializedName("data")
    val data: CoinRateDataWrapper,

    @SerializedName("message")
    val message: String,

    @SerializedName("status")
    val status: Int,
)

data class CoinRateDataWrapper(
    @SerializedName("data")
    val data: Map<String, CoinRate>,
)

data class CoinRate(
    @SerializedName("circulating_supply")
    val circulatingSupply: String,
    @SerializedName("cmc_rank")
    val cmcRank: Int,
    @SerializedName("date_added")
    val dateAdded: String,
    @SerializedName("id")
    val id: Int,
    @SerializedName("is_active")
    val isActive: Int,
    @SerializedName("is_fiat")
    val isFiat: Int,
    @SerializedName("last_updated")
    val lastUpdated: String,
    @SerializedName("max_supply")
    val maxSupply: Long,
    @SerializedName("name")
    val name: String,
    @SerializedName("num_market_pairs")
    val numMarketPairs: Int,
    @SerializedName("self_reported_circulating_supply")
    val selfReportedCirculatingSupply: String,
    @SerializedName("self_reported_market_cap")
    val selfReportedMarketCap: String,
    @SerializedName("slug")
    val slug: String,
    @SerializedName("symbol")
    val symbol: String,
//    @SerializedName("tags")
//    val tags: String,
    @SerializedName("total_supply")
    val totalSupply: String,
//    @SerializedName("platform")
//    val platform:String,
    @SerializedName("quote")
    val quote: Map<String, CoinRateQuote>,
) {
    fun updateTime() = lastUpdated.gmtToTs()

}

data class CoinRateQuote(
    @SerializedName("fully_diluted_market_cap")
    val fullyDilutedMarketCap: Float,
    @SerializedName("last_updated")
    val lastUpdated: String,
    @SerializedName("market_cap")
    val marketCap: Float,
    @SerializedName("market_cap_dominance")
    val marketCapDominance: Double,
    @SerializedName("percent_change_1h")
    val percentChange1h: Float,
    @SerializedName("percent_change_24h")
    val percentChange24h: Float,
    @SerializedName("percent_change_30d")
    val percentChange30d: Float,
    @SerializedName("percent_change_60d")
    val percentChange60d: Float,
    @SerializedName("percent_change_7d")
    val percentChange7d: Float,
    @SerializedName("percent_change_90d")
    val percentChange90d: Float,
    @SerializedName("price")
    val price: Float,
    @SerializedName("volume_24h")
    val volume24h: Float,
    @SerializedName("volume_change_24h")
    val volumeChange24h: Float,
) {
    fun updateTime() = lastUpdated.gmtToTs()
}