package com.flowfoundation.wallet.network.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

data class SwapEstimateResponse(
    @SerializedName("data")
    val data: Data,
    @SerializedName("message")
    val message: String?,
    @SerializedName("status")
    val status: Int?
) {

    @Parcelize
    data class Data(
        @SerializedName("priceImpact")
        val priceImpact: Float,
        @SerializedName("routes")
        val routes: List<Route?>,
        @SerializedName("tokenInAmount")
        val tokenInAmount: BigDecimal,
        @SerializedName("tokenInKey")
        val tokenInKey: String,
        @SerializedName("tokenOutAmount")
        val tokenOutAmount: BigDecimal,
        @SerializedName("tokenOutKey")
        val tokenOutKey: String
    ) : Parcelable {

        @Parcelize
        data class Route(
            @SerializedName("route")
            val route: List<String>,
            @SerializedName("routeAmountIn")
            val routeAmountIn: BigDecimal,
            @SerializedName("routeAmountOut")
            val routeAmountOut: BigDecimal
        ) : Parcelable
    }
}