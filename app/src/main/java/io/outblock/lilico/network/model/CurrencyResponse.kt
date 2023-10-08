package io.outblock.lilico.network.model


import com.google.gson.annotations.SerializedName

data class CurrencyResponse(
    @SerializedName("data")
    val data: CurrencyData,
)

data class CurrencyData(
    @SerializedName("result")
    val result: Float,
    @SerializedName("success")
    val success: Boolean
)