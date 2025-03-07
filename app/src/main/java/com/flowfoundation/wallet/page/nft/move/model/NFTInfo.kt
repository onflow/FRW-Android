package com.flowfoundation.wallet.page.nft.move.model

import com.google.gson.annotations.SerializedName

data class NFTInfo(
    @SerializedName("id")
    val id: String,
    @SerializedName("cover")
    val cover: String
)