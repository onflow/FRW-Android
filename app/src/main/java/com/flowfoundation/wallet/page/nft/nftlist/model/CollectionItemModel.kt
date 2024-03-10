package com.flowfoundation.wallet.page.nft.nftlist.model

import com.flowfoundation.wallet.manager.config.NftCollection

data class CollectionItemModel(
    val count: Int,
    val collection: NftCollection,
    var isSelected: Boolean = false,
)