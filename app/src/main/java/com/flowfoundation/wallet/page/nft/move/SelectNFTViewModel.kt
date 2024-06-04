package com.flowfoundation.wallet.page.nft.move

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.config.NftCollection
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.retrofitApi
import com.flowfoundation.wallet.page.nft.move.model.CollectionDetailInfo
import com.flowfoundation.wallet.page.nft.move.model.CollectionInfo
import com.flowfoundation.wallet.page.nft.move.model.NFTInfo
import com.flowfoundation.wallet.page.nft.nftlist.cover
import com.flowfoundation.wallet.page.nft.nftlist.nftWalletAddress
import com.flowfoundation.wallet.utils.viewModelIOScope


class SelectNFTViewModel : ViewModel() {

    companion object {
        const val SELECT_NFT_LIMIT = 10
    }

    val nftListLiveData = MutableLiveData<List<NFTInfo>>()
    val collectionLiveData = MutableLiveData<CollectionInfo?>()
    val moveCountLiveData = MutableLiveData<Int>()
    private val selectedNFTIdList = mutableListOf<String>()
    private val service by lazy { retrofitApi().create(ApiService::class.java) }
    private var collectionContract: String? = null
    private var collectionAddress: String? = null

    fun loadCollections() {
        viewModelIOScope(this) {
            val address = nftWalletAddress()
            val collectionResponse = service.getNFTCollections(address)
            if (collectionResponse.data.isNullOrEmpty()) {
                postEmpty()
                return@viewModelIOScope
            }
            val collection =
                collectionResponse.data.firstOrNull { it.collection != null }?.collection
            if (collection == null) {
                postEmpty()
                return@viewModelIOScope
            }
            collectionLiveData.postValue(
                CollectionInfo(
                    id = collection.id,
                    name = collection.name,
                    logo = collection.logo()
                )
            )
            this.collectionContract = collection.contractName
            this.collectionAddress = collection.address
            loadNFTList(collection.id)
        }
    }

    fun loadEVMCollections() {
        viewModelIOScope(this) {
            val address = WalletManager.selectedWalletAddress()
            val collectionsResponse = service.getEVMNFTCollections(address)
            if (collectionsResponse.data.isNullOrEmpty()) {
                postEmpty()
                return@viewModelIOScope
            }
            val collection = collectionsResponse.data.firstOrNull { it.flowIdentifier.isNullOrEmpty().not() && it.nftList.isNotEmpty() }
            if (collection == null) {
                postEmpty()
                return@viewModelIOScope
            }
            collectionLiveData.postValue(
                CollectionInfo(
                    id = collection.getId(),
                    name = collection.name,
                    logo = collection.logo()
                )
            )
            val list = collection.nftList.map {
                NFTInfo(
                    id = it.id,
                    cover = it.thumb
                )
            }.toList()
            this.collectionContract = collection.getContractName()
            this.collectionAddress = collection.getContractAddress()
            nftListLiveData.postValue(list)
            selectedNFTIdList.clear()
        }
    }

    private fun postEmpty() {
        collectionLiveData.postValue(null)
        nftListLiveData.postValue(emptyList())
        selectedNFTIdList.clear()
    }

    fun setCollectionInfo(detailInfo: CollectionDetailInfo) {
        this.collectionContract = detailInfo.contractName
        this.collectionAddress = detailInfo.contractAddress
        collectionLiveData.postValue(
            CollectionInfo(
                id = detailInfo.id,
                name = detailInfo.name,
                logo = detailInfo.logo
            )
        )
        selectedNFTIdList.clear()
        if (detailInfo.isFlowCollection) {
            loadNFTList(detailInfo.id)
        } else {
            nftListLiveData.postValue(detailInfo.nftList ?: emptyList())
        }
    }

    private fun loadNFTList(collectionId: String) {
        viewModelIOScope(this) {
            val nftListResponse = service.getNFTListOfCollection(
                nftWalletAddress(), collectionId,
                0, 20
            )
            val list = nftListResponse.data?.nfts?.map {
                NFTInfo(
                    id = it.id,
                    cover = it.cover() ?: ""
                )
            }?.toList() ?: emptyList()
            nftListLiveData.postValue(list)
            selectedNFTIdList.clear()
        }
    }

    suspend fun moveSelectedNFT(isMoveToEVM: Boolean, callback: (isSuccess: Boolean) -> Unit) {
        if (collectionContract == null || collectionAddress == null) {
            callback.invoke(false)
            return
        }
        EVMWalletManager.moveNFTList(
            collectionContract!!,
            collectionAddress!!,
            selectedNFTIdList,
            isMoveToEVM,
            callback
        )
    }

    fun isNFTSelected(nftId: String): Boolean {
        return selectedNFTIdList.contains(nftId)
    }

    fun isSelectedToLimit(): Boolean {
        return selectedNFTIdList.size == SELECT_NFT_LIMIT
    }

    fun selectNFT(nftId: String) {
        selectedNFTIdList.add(nftId)
        moveCountLiveData.postValue(selectedNFTIdList.size)
    }

    fun unSelectNFT(nftId: String) {
        selectedNFTIdList.remove(nftId)
        moveCountLiveData.postValue(selectedNFTIdList.size)
    }

}