package com.flowfoundation.wallet.network.model

import androidx.annotation.WorkerThread
import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.page.nft.nftlist.nftWalletAddress
import com.flowfoundation.wallet.page.nft.nftlist.utils.NftCache

data class NftFavoriteResponse(
    @SerializedName("data")
    val data: NftFavoriteData?,

    @SerializedName("message")
    val message: String,

    @SerializedName("status")
    val status: Int,
)

data class NftFavoriteData(
    @SerializedName("nfts")
    internal val nfts: List<Nft>?,
    @SerializedName("list")
    val ids: String?,
    @SerializedName("chain")
    val chain: String,
    @SerializedName("network")
    val network: String,
    @SerializedName("nftCount")
    val nftCount: Int,
)

@WorkerThread
fun NftFavoriteData.nfts(): List<Nft> {
    if (ids.isNullOrBlank()) {
        return emptyList()
    }

    val nftCache = NftCache(nftWalletAddress())
    return ids.split(",").map { it.trim() }.distinct()
        .mapNotNull { id -> nfts?.firstOrNull { it.serverId() == id } ?: nftCache.findNftById(id) }
}