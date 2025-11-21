package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName


data class CoinbaseOnRampRequest(
    @SerializedName("address")
    val address: String,
)
