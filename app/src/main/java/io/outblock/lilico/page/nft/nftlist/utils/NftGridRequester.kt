package io.outblock.lilico.page.nft.nftlist.utils

import io.outblock.lilico.network.ApiService
import io.outblock.lilico.network.model.Nft
import io.outblock.lilico.network.retrofitApi
import io.outblock.lilico.page.nft.nftlist.nftWalletAddress

class NftGridRequester {
    private val limit = 24
    private var offset = 0

    private var count = -1

    private val service by lazy { retrofitApi().create(ApiService::class.java) }

    private val dataList = mutableListOf<Nft>()

    private var isLoadMoreRequesting = false

    fun cachedNfts() = cache().grid().read() ?: NftList()

    suspend fun request(): NftList {
        resetOffset()
        dataList.clear()
        val address = nftWalletAddress()
        if (address.isEmpty()) {
            count = 0
            return NftList()
        }
        val response = service.nftList(address, offset, limit)
        if (response.status > 200) {
            throw Exception("request grid list error: $response")
        }

        dataList.clear()

        if (response.data == null) {
            count = 0
            return NftList()
        }

        count = response.data.nftCount

        cache().grid().cacheSync(NftList(dataList.toList(), count = count))

        dataList.addAll(response.data.nfts.orEmpty())

        return NftList(list = response.data.nfts.orEmpty(), count = response.data.nftCount)
    }

    suspend fun nextPage(): NftList {
        if (isLoadMoreRequesting) throw RuntimeException("load more is running")

        val address = nftWalletAddress()
        if (address.isEmpty()) {
            return NftList()
        }

        isLoadMoreRequesting = true
        offset += limit
        val response = service.nftList(nftWalletAddress(), offset, limit)
        response.data ?: return NftList()
        count = response.data.nftCount

        dataList.addAll(response.data.nfts.orEmpty())

        cache().grid().cacheSync(NftList(dataList.toList(), count = count))

        isLoadMoreRequesting = false
        return NftList(list = response.data.nfts.orEmpty(), count = response.data.nftCount)
    }

    fun count() = count

    fun dataList(): List<Nft> {
        return if (dataList.isEmpty()) cachedNfts().list else dataList
    }

    fun haveMore() = count > limit && offset < count

    private fun resetOffset() = apply {
        offset = 0
        count = -1
        isLoadMoreRequesting = false
    }

    private fun cache() = NftCache(nftWalletAddress())
}