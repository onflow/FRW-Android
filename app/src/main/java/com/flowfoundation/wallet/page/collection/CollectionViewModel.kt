package com.flowfoundation.wallet.page.collection

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.network.model.NftCollectionWrapper
import com.flowfoundation.wallet.page.nft.nftlist.model.NFTItemModel
import com.flowfoundation.wallet.page.nft.nftlist.model.NftLoadMoreModel
import com.flowfoundation.wallet.page.nft.nftlist.nftWalletAddress
import com.flowfoundation.wallet.page.nft.nftlist.utils.NftCache
import com.flowfoundation.wallet.page.nft.nftlist.utils.NftListRequester
import com.flowfoundation.wallet.utils.viewModelIOScope

class CollectionViewModel : ViewModel() {
    val dataLiveData = MutableLiveData<List<Any>>()
    val collectionLiveData = MutableLiveData<NftCollectionWrapper>()

    private var collectionWrapper: NftCollectionWrapper? = null

    private val nftCache by lazy { NftCache(nftWalletAddress()) }

    private val requester by lazy { NftListRequester() }

    fun load(contractName: String) {
        viewModelIOScope(this) {
            val collectionWrapper = nftCache.collection()
                .read()?.collections
                ?.firstOrNull { it.collection?.contractName == contractName } ?: return@viewModelIOScope

            this.collectionWrapper = collectionWrapper

            val collection = collectionWrapper.collection ?: return@viewModelIOScope

            collectionLiveData.postValue(collectionWrapper)
            notifyNftList()

            requester.request(collection)
            notifyNftList()
        }
    }

    fun requestListNextPage() {
        viewModelIOScope(this) {
            val collection = collectionWrapper?.collection ?: return@viewModelIOScope
            requester.nextPage(collection)
            notifyNftList()
        }
    }

    private fun notifyNftList() {
        val collection = collectionWrapper?.collection ?: return
        val list = mutableListOf<Any>().apply { addAll(requester.dataList(collection).map { NFTItemModel(nft = it) }) }
        if (requester.haveMore()) {
            list.add(NftLoadMoreModel(isListLoadMore = true))
        }
        dataLiveData.postValue(list)
    }
}