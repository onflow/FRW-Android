package com.flowfoundation.wallet.page.nft.nftlist.utils

import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.network.retrofitApi
import com.flowfoundation.wallet.page.nft.nftlist.nftWalletAddress

class NftGridRequester {
    private val limit = 24
    private var offset = 0
    private var evmOffset = ""

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
        val response = if (WalletManager.isEVMAccountSelected()) {
            service.getEVMNFTList(address, evmOffset, limit)
        } else {
            service.getNFTList(address, offset, limit)
        }

        if (response.status > 200) {
            throw Exception("request grid list error: $response")
        }

        dataList.clear()

        if (response.data == null) {
            count = 0
            return NftList()
        }

        if (WalletManager.isEVMAccountSelected()) {
            evmOffset = response.data.offset.orEmpty()
        }

        count = response.data.nftCount

        dataList.addAll(response.data.nfts.orEmpty())

        cache().grid().cacheSync(NftList(dataList.toList(), count = count))

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
        val response = if (WalletManager.isEVMAccountSelected()) {
            service.getEVMNFTList(address, evmOffset, limit)
        } else {
            service.getNFTList(nftWalletAddress(), offset, limit)
        }

        response.data ?: return NftList()

        if (WalletManager.isEVMAccountSelected()) {
            evmOffset = response.data.offset.orEmpty()
        }

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