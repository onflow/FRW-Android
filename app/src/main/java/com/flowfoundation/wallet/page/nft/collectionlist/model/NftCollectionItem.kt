package com.flowfoundation.wallet.page.nft.collectionlist.model

import com.flowfoundation.wallet.manager.config.NftCollection
import com.google.gson.annotations.SerializedName

data class NftCollectionItem(
    @SerializedName("collection")
    val collection: NftCollection,
    @SerializedName("isAdded")
    var isAdded: Boolean,
    @SerializedName("isAdding")
    var isAdding: Boolean? = null,
) {
    fun isNormalState() = !(isAdded || isAdding == true)
}