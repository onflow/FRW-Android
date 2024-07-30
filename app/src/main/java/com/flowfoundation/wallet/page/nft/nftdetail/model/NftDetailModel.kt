package com.flowfoundation.wallet.page.nft.nftdetail.model

import com.flowfoundation.wallet.network.model.Nft

class NftDetailModel(
    val nft: Nft? = null,
    val onPause: Boolean? = null,
    val onRestart: Boolean? = null,
    val onDestroy: Boolean? = null,
    val fromAddress: String? = null
)