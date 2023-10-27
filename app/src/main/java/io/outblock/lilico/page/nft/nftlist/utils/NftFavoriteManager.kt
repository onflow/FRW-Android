package io.outblock.lilico.page.nft.nftlist.utils

import com.google.gson.annotations.SerializedName
import io.outblock.lilico.cache.CacheManager
import io.outblock.lilico.cache.cacheFile
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.network.ApiService
import io.outblock.lilico.network.model.*
import io.outblock.lilico.network.retrofit
import io.outblock.lilico.page.nft.nftlist.nftWalletAddress
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.uiScope
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

object NftFavoriteManager {

    private val listeners = CopyOnWriteArrayList<WeakReference<OnNftFavoriteChangeListener>>()

    private val service by lazy { retrofit().create(ApiService::class.java) }

    private val favoriteList = mutableListOf<Nft>()

    fun addOnNftSelectionChangeListener(listener: OnNftFavoriteChangeListener) {
        listeners.add(WeakReference(listener))
    }

    suspend fun request() {
        dispatchListener(cache().read()?.nfts.orEmpty())
        fetchFromServer()
    }

    fun addFavorite(nft: Nft) {
        ioScope {
            val favorites = favoriteList().toMutableList()

            dispatchListener(favorites.apply { add(0, nft) })
            cache().cacheSync(FavoriteCache(favorites))

            val address = WalletManager.selectedWalletAddress() ?: return@ioScope
            val resp = service.addNftFavorite(AddNftFavoriteRequest(address, nft.contractName().orEmpty(), nft.tokenId()))
            if (resp.status == 200) {
                fetchFromServer()
            }
        }
    }

    fun removeFavorite(contractName: String?, tokenId: String?) {
        contractName ?: return
        tokenId ?: return
        ioScope {
            val favorites = favoriteList().toMutableList()
            favorites.removeAll { it.contractName() == contractName && it.tokenId() == tokenId }
            dispatchListener(favorites)
            cache().cacheSync(FavoriteCache(favorites))

            val resp = updateFavorite(favorites.map { it.serverId() })
            if (resp.status == 200) {
                favorites.removeAll { it.contractName() == contractName && it.tokenId() == tokenId }
                fetchFromServer()
            }
        }
    }

    fun favoriteList() = if (favoriteList.isEmpty()) cache().read()?.nfts.orEmpty() else favoriteList.toList()

    fun isFavoriteNft(nft: Nft) = favoriteList().firstOrNull { it.uniqueId() == nft.uniqueId() } != null

    private suspend fun fetchFromServer() {
        val address = nftWalletAddress()
        if (address.isEmpty()) {
            return
        }
        val response = service.getNftFavorite(address)
        val nfts = response.data?.nfts() ?: emptyList()

        cache().cacheSync(FavoriteCache(nfts))

        dispatchListener(nfts)
    }

    private suspend fun updateFavorite(ids: List<String>): CommonResponse {
        return service.updateFavorite(UpdateNftFavoriteRequest(ids.map { it.trim() }.distinct().joinToString(",")))
    }

    private fun cache() = CacheManager("${nftWalletAddress()}_nft_favorite".cacheFile(), FavoriteCache::class.java)

    private fun dispatchListener(nfts: List<Nft>) {
        favoriteList.clear()
        favoriteList.addAll(nfts)
        uiScope {
            listeners.removeAll { it.get() == null }
            listeners.forEach { it.get()?.onNftFavoriteChange(nfts) }
        }
    }

}

interface OnNftFavoriteChangeListener {
    fun onNftFavoriteChange(nfts: List<Nft>)
}

private data class FavoriteCache(
    @SerializedName("ids")
    val nfts: List<Nft>,
)