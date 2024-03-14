package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName

data class AddNftFavoriteRequest(
    @SerializedName("address")
    val address: String,
    @SerializedName("contract")
    val contractName: String,
    @SerializedName("ids")
    val tokenId: String,
)