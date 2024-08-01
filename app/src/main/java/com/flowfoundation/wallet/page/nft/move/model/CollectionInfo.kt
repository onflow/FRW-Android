package com.flowfoundation.wallet.page.nft.move.model


data class CollectionInfo (
    val id: String,
    val name: String,
    val logo: String
)

data class CollectionDetailInfo(
    val id: String,
    val name: String,
    val logo: String,
    val contractName: String,
    val contractAddress: String,
    val count: Int,
    val isFlowCollection: Boolean,
    val nftList: List<NFTInfo>? = null,
    val identifier: String
)