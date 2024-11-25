package com.flowfoundation.wallet.network.model


import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class CryptowatchSummaryResponse(
    @SerializedName("data")
    val data: CryptowatchSummaryData,
)

data class CryptowatchSummaryData(
    @SerializedName("allowance")
    val allowance: Allowance,
    @SerializedName("result")
    val result: Result
) {
    data class Allowance(
        @SerializedName("cost")
        val cost: Float,
        @SerializedName("remaining")
        val remaining: Float,
    )

    data class Result(
        @SerializedName("price")
        val price: Price,
        @SerializedName("volume")
        val volume: Float,
        @SerializedName("volumeQuote")
        val volumeQuote: Float
    ) {
        data class Price(
            @SerializedName("change")
            val change: Change,
            @SerializedName("high")
            val high: BigDecimal,
            @SerializedName("last")
            val last: BigDecimal,
            @SerializedName("low")
            val low: BigDecimal
        ) {
            data class Change(
                @SerializedName("absolute")
                val absolute: Float,
                @SerializedName("percentage")
                val percentage: Float
            )
        }
    }
}