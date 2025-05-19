package com.flowfoundation.wallet.page.nft.nftlist.utils

import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.cache.CacheManager
import com.flowfoundation.wallet.cache.cacheFile
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.*
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.page.nft.nftlist.nftWalletAddress
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.uiScope
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

            val address = WalletManager.selectedWalletAddress()
            val resp = service.addNftFavorite(AddNftFavoriteRequest(address, nft.contractName(), nft.tokenId()))
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
            val resp = updateFavorite(favorites.map { it.serverId() })
            if (resp.status == 200) {
                fetchFromServer()
            }
            dispatchListener(favorites)
            cache().cacheSync(FavoriteCache(favorites))
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