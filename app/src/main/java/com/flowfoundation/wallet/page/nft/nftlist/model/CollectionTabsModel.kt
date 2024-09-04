package com.flowfoundation.wallet.page.nft.nftlist.model

import com.google.gson.annotations.SerializedName

data class CollectionTabsModel(
    @SerializedName("collections")
    val collections: List<CollectionItemModel>? = null,
    @SerializedName("isExpand")
    val isExpand: Boolean? = null,
)