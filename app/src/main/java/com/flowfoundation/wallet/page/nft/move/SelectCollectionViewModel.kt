package com.flowfoundation.wallet.page.nft.move

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.app.isPreviewnet
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.retrofitApi
import com.flowfoundation.wallet.page.nft.move.model.CollectionDetailInfo
import com.flowfoundation.wallet.page.nft.move.model.NFTInfo
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
            val collectionResponse = if(EVMWalletManager.evmFeatureAvailable())
                service.getNFTCollections(address)
            else
                service.nftCollectionsOfAccount(address)
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
                    contractName = collection.contractName,
                    contractAddress = collection.address,
                    count = it.count ?: 0,
                    isFlowCollection = true,
                    identifier = collection.path.privatePath?.removePrefix("/private/") ?: ""
                )
            }
            collectionList.addAll(collections)
            collectionListLiveData.postValue(
                collections
            )
        }
    }

    fun loadEVMCollections() {
        viewModelIOScope(this) {
            collectionList.clear()
            val address = WalletManager.selectedWalletAddress()
            val collectionsResponse = service.getEVMNFTCollections(address)
            if (collectionsResponse.data.isNullOrEmpty()) {
                collectionListLiveData.postValue(emptyList())
                return@viewModelIOScope
            }
            val collections = collectionsResponse.data.filter {
                it.flowIdentifier.isNullOrEmpty().not()
            }.map {
                CollectionDetailInfo(
                    id = it.getId(),
                    name = it.name,
                    logo = it.logo(),
                    contractName = it.getContractName(),
                    contractAddress = it.getContractAddress(),
                    count = it.nftList.size,
                    identifier = "",
                    isFlowCollection = false,
                    nftList = it.nftList.map { nft ->
                        NFTInfo(
                            id = nft.id,
                            cover = nft.thumb
                        )
                    }
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