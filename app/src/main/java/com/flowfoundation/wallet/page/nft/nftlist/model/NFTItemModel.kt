package com.flowfoundation.wallet.page.nft.nftlist.model

import com.flowfoundation.wallet.network.model.Nft

data class NFTItemModel(
    val index: Int = 0,
    val nft: Nft,
    val accountAddress: String? = null
)