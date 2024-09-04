package com.flowfoundation.wallet.page.nft.nftlist.model

import com.flowfoundation.wallet.network.model.Nft
import com.google.gson.annotations.SerializedName

data class NFTItemModel(
    @SerializedName("index")
    val index: Int = 0,
    @SerializedName("nft")
    val nft: Nft,
    @SerializedName("accountAddress")
    val accountAddress: String? = null
)