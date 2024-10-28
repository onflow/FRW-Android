package com.flowfoundation.wallet.page.nft.move

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.retrofitApi
import com.flowfoundation.wallet.page.nft.move.model.CollectionDetailInfo
import com.flowfoundation.wallet.page.nft.nftlist.nftWalletAddress
import com.flowfoundation.wallet.utils.viewModelIOScope


class SelectCollectionViewModel : ViewModel() {

    private val collectionList = mutableListOf<CollectionDetailInfo>()

    val collectionListLiveData = MutableLiveData<List<CollectionDetailInfo>>()
    private val service by lazy { retrofitApi().create(ApiService::class.java) }

    private var keyword = ""

    fun loadCollections() {
        viewModelIOScope(this) {
            collectionList.clear()
            val address = nftWalletAddress()
            val collectionResponse = if (EVMWalletManager.isEVMWalletAddress(address)) {
                service.getEVMNFTCollections(address)
            } else {
                service.getNFTCollections(address)
            }
            if (collectionResponse.data.isNullOrEmpty()) {
                collectionListLiveData.postValue(emptyList())
                return@viewModelIOScope
            }
            val collections = collectionResponse.data.filter {
                it.collection != null
            }.map {
                val collection = it.collection!!
                CollectionDetailInfo(
                    id = collection.id,
                    name = collection.name,
                    logo = collection.logo(),
                    contractName = collection.contractName(),
                    contractAddress = collection.address.orEmpty(),
                    count = it.count ?: 0,
                    isFlowCollection = EVMWalletManager.isEVMWalletAddress(address).not(),
                    identifier = collection.path?.privatePath?.removePrefix("/private/") ?: "",
                    nftIdentifier = collection.getNFTIdentifier()
                )
            }
            collectionList.addAll(collections)
            collectionListLiveData.postValue(
                collections
            )
        }
    }

    fun search(keyword: String) {
        this.keyword = keyword
        if (keyword.isBlank()) {
            collectionListLiveData.postValue(collectionList.toList())
        } else {
            collectionListLiveData.postValue(collectionList.filter {
                it.name.lowercase().contains(keyword.lowercase())
                        || it.contractName.lowercase().contains(keyword.lowercase())
            })
        }
    }

    fun clearSearch() {
        this.keyword = ""
        search("")
    }
}