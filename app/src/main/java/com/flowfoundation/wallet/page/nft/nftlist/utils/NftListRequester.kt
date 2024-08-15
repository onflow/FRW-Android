package com.flowfoundation.wallet.page.nft.nftlist.utils

import com.flowfoundation.wallet.manager.config.NftCollection
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.network.model.NftCollectionWrapper
import com.flowfoundation.wallet.network.model.NftCollections
import com.flowfoundation.wallet.network.retrofitApi
import com.flowfoundation.wallet.page.nft.nftlist.nftWalletAddress

class NftListRequester {
    private val limit = 24
    private var offset = 0

    private var count = -1

    private var isLoadMoreRequesting = false

    private val service by lazy { retrofitApi().create(ApiService::class.java) }

    private val dataList = mutableListOf<Nft>()

    private val collectionList = mutableListOf<NftCollectionWrapper>()

    fun cacheCollections() = cache().collection().read()?.collections?.sort()

    suspend fun requestCollection(): List<NftCollectionWrapper>? {
        val address = nftWalletAddress()
        if (address.isEmpty()) {
            return emptyList()
        }
        val collectionResponse = if (EVMWalletManager.evmFeatureAvailable()) {
            service.getNFTCollections(nftWalletAddress())
        } else {
            service.nftCollectionsOfAccount(nftWalletAddress())
        }
        if (collectionResponse.status > 200) {
            throw Exception("request nft list error: $collectionResponse")
        }
        val collections = if (EVMWalletManager.evmFeatureAvailable()) {
            collectionResponse.data?.filter { it.collection?.address?.isNotBlank() == true && it.ids?.isNotEmpty() == true }.orEmpty()
        } else {
            collectionResponse.data?.filter { it.collection?.address?.isNotBlank() == true }.orEmpty()
        }
        collectionList.clear()
        collectionList.addAll(collections)
        cache().collection().cacheSync(NftCollections(collections))
        return collections.sort()
    }

    fun cachedNfts(collection: NftCollection) = cache().list(collection.contractName).read()?.list

    fun cacheCollections(collections: List<NftCollectionWrapper>) {
        cache().collection().cacheSync(NftCollections(collections))
    }

    fun cacheCollectionNFTs(contractName: String, list: List<Nft>) {
        cache().list(contractName).cacheSync(NftList(list.toList()))
    }

    suspend fun request(collection: NftCollection, accountAddress: String = nftWalletAddress()): List<Nft> {
        resetOffset()
        dataList.clear()
        val response = if (EVMWalletManager.evmFeatureAvailable()) {
            service.getNFTListOfCollection(accountAddress, collection.id, offset, limit)
        } else {
            service.nftsOfCollection(accountAddress, collection.id, offset, limit)
        }
        if (response.status > 200) {
            throw Exception("request nft list error: $response")
        }

        dataList.clear()

        if (response.data == null) {
            count = 0
            return emptyList()
        }

        count = response.data.nftCount

        dataList.addAll(response.data.nfts.orEmpty())

        cache().list(collection.contractName).cacheSync(NftList(dataList.toList()))

        return response.data.nfts.orEmpty()
    }

    suspend fun nextPage(collection: NftCollection): List<Nft> {
        if (isLoadMoreRequesting) throw RuntimeException("load more is running")
        isLoadMoreRequesting = true

        offset += limit
        val response = if (EVMWalletManager.evmFeatureAvailable()) {
            service.getNFTListOfCollection(nftWalletAddress(), collection.id, offset, limit)
        } else {
            service.nftsOfCollection(nftWalletAddress(), collection.id, offset, limit)
        }
        response.data ?: return emptyList()
        count = response.data.nftCount

        dataList.addAll(response.data.nfts.orEmpty())

        cache().list(collection.contractName).cacheSync(NftList(dataList.toList()))

        isLoadMoreRequesting = false

        return response.data.nfts.orEmpty()
    }

    fun count() = count

    fun haveMore() = count > limit && offset < count

    fun dataList(collection: NftCollection): List<Nft> {
        val list = if (dataList.firstOrNull()?.contract?.name == collection.contractName) dataList.toList() else emptyList()
        return list.ifEmpty { cachedNfts(collection).orEmpty() }
    }

    private fun resetOffset() = apply {
        offset = 0
        count = -1
        isLoadMoreRequesting = false
    }

    private fun cache() = NftCache(nftWalletAddress())

    private fun List<NftCollectionWrapper>?.sort(): List<NftCollectionWrapper>? {
        this ?: return null
        return this.sortedBy { it.collection?.name?.take(1) }.sortedByDescending { it.count }
    }
}