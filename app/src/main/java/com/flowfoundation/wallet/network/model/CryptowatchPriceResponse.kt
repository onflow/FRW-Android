package com.flowfoundation.wallet.network.model


import com.google.gson.annotations.SerializedName

data class CryptowatchPriceResponse(
    @SerializedName("allowance")
    val allowance: Allowance,
    @SerializedName("result")
    val result: Result
) {
    data class Allowance(
        @SerializedName("cost")
        val cost: Double,
        @SerializedName("remaining")
        val remaining: Double,
    )

    data class Result(
        @SerializedName("price")
        val price: Float
    )
}