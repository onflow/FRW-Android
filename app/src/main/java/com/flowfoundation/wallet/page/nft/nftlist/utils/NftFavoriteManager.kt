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
import com.google.gson.reflect.TypeToken

object NftFavoriteManager {

    private val TAG = NftFavoriteManager::class.java.simpleName

    private val listeners = CopyOnWriteArrayList<WeakReference<OnNftFavoriteChangeListener>>()

    private val service by lazy { retrofit().create(ApiService::class.java) }

    private var favoriteList = mutableListOf<Nft>()

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
            if (favorites.none { it.uniqueId() == nft.uniqueId() }) {
                favorites.add(0, nft)
                dispatchListener(favorites)
                cache().cacheSync(FavoriteCache(favorites))
            }

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
            val originalSize = favorites.size
            favorites.removeAll { it.contractName() == contractName && it.tokenId() == tokenId }
            
            if (favorites.size < originalSize) {
                dispatchListener(favorites)
                cache().cacheSync(FavoriteCache(favorites))
                val resp = updateFavorite(favorites.map { it.uniqueId() })
                if (resp.status == 200) {
                    fetchFromServer()
                }
            }
        }
    }

    fun favoriteList() = if (favoriteList.isEmpty()) cache().read()?.nfts.orEmpty() else favoriteList.toList()

    fun isFavoriteNft(nft: Nft) = favoriteList().any { it.uniqueId() == nft.uniqueId() }

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

    private fun cache() = CacheManager<FavoriteCache>("${nftWalletAddress()}_nft_favorite".cacheFile(), object : TypeToken<FavoriteCache>() {}.type)

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