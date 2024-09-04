package com.flowfoundation.wallet.page.nft.nftlist.model

import com.flowfoundation.wallet.manager.config.NftCollection
import com.google.gson.annotations.SerializedName

data class CollectionItemModel(
    @SerializedName("count")
    val count: Int,
    @SerializedName("collection")
    val collection: NftCollection,
    @SerializedName("isSelected")
    var isSelected: Boolean = false,
)