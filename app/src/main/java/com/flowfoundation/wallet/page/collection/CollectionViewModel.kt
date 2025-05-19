package com.flowfoundation.wallet.page.collection

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.config.NftCollection
import com.flowfoundation.wallet.manager.config.NftCollectionConfig
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

    private var collection: NftCollection? = null

    private val requester by lazy { NftListRequester() }
    private var accountAddress: String? = null

    fun load(contractId: String, contractName: String, accountAddress: String, collectionSize: Int) {
        this.accountAddress = accountAddress
        viewModelIOScope(this) {
            if (accountAddress.isEmpty()) {
                val collectionWrapper =
                    NftCache(nftWalletAddress())
                        .collection()
                        .read()?.collections
                        ?.firstOrNull { it.collection?.id == contractId && it.collection.contractName() == contractName }
                        ?: return@viewModelIOScope

                this.collection = collectionWrapper.collection

                collection?.let {
                    collectionLiveData.postValue(collectionWrapper)
                    notifyNftList()

                    requester.request(it, nftWalletAddress())
                    notifyNftList()
                }
            } else {
                val collection = NftCollectionConfig.get(contractName = contractName) ?: return@viewModelIOScope
                this.collection = collection
                collectionLiveData.postValue(NftCollectionWrapper(
                    collection = collection,
                    count = collectionSize,
                    ids = emptyList()
                ))
                requester.request(collection, accountAddress)
                notifyNftList()
            }
        }
    }

    fun requestListNextPage() {
        viewModelIOScope(this) {
            collection?.let {
                requester.nextPage(it)
                notifyNftList()
            }
        }
    }

    private fun notifyNftList() {
        collection?.let {
            val list = mutableListOf<Any>().apply {
                addAll(
                    requester.dataList(it).map { NFTItemModel(nft = it, accountAddress = accountAddress) })
            }
            if (requester.haveMore()) {
                list.add(NftLoadMoreModel(isListLoadMore = true))
            }
            dataLiveData.postValue(list)
        }
    }
}