package com.flowfoundation.wallet.page.nft.move.model

import com.google.gson.annotations.SerializedName

data class NFTInfo(
    @SerializedName("id")
    val id: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("cover")
    val cover: String,
    @SerializedName("description")
    val description: String = "",
    @SerializedName("traits")
    val traits: List<String> = emptyList()
)