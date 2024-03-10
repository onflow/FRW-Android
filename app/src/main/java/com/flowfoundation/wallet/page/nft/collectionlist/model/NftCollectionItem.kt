package com.flowfoundation.wallet.page.nft.collectionlist.model

import com.flowfoundation.wallet.manager.config.NftCollection

data class NftCollectionItem(
    val collection: NftCollection,
    var isAdded: Boolean,
    var isAdding: Boolean? = null,
) {
    fun isNormalState() = !(isAdded || isAdding == true)
}