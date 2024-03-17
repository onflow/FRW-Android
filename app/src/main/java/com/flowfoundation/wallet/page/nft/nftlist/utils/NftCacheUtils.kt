package com.flowfoundation.wallet.page.nft.nftlist.utils

import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.cache.CacheManager
import com.flowfoundation.wallet.cache.cacheFile
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.network.model.NftCollections


class NftCache(
    private val address: String
) {

    fun grid(): CacheManager<NftList> {
        return CacheManager("${address}_nft_grid".cacheFile(), NftList::class.java)
    }

    fun collection(): CacheManager<NftCollections> {
        return CacheManager("${address}_nft_collection".cacheFile(), NftCollections::class.java)
    }

    fun list(contractName: String): CacheManager<NftList> {
        return CacheManager("${address}_${contractName}_nft_list".cacheFile(), NftList::class.java)
    }

    fun findNftById(uniqueId: String): Nft? {
        return grid().read()?.list?.firstOrNull { it.uniqueId() == uniqueId } ?: findNftFromCollection(uniqueId)
    }

    private fun findNftFromCollection(uniqueId: String): Nft? {
        val collections = collection().read()?.collections?.mapNotNull { it.collection?.contractName } ?: return null
        for (collection in collections) {
            val nfts = list(collection).read()?.list ?: continue
            return nfts.firstOrNull { it.uniqueId() == uniqueId } ?: continue
        }
        return null
    }
}

data class NftList(
    @SerializedName("list")
    val list: List<Nft> = emptyList(),
    @SerializedName("count")
    val count: Int = 0,
)