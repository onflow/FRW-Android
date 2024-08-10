package com.flowfoundation.wallet.page.nft.move

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.app.isPreviewnet
import com.flowfoundation.wallet.manager.config.NftCollectionConfig
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceMoveNFTListFromChildToParent
import com.flowfoundation.wallet.manager.flowjvm.cadenceSendNFTListFromChildToChild
import com.flowfoundation.wallet.manager.flowjvm.cadenceSendNFTListFromParentToChild
import com.flowfoundation.wallet.manager.transaction.TransactionStateWatcher
import com.flowfoundation.wallet.manager.transaction.isExecuteFinished
import com.flowfoundation.wallet.manager.transaction.isFailed
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.retrofitApi
import com.flowfoundation.wallet.page.nft.move.model.CollectionDetailInfo
import com.flowfoundation.wallet.page.nft.move.model.CollectionInfo
import com.flowfoundation.wallet.page.nft.move.model.NFTInfo
import com.flowfoundation.wallet.page.nft.nftlist.cover
import com.flowfoundation.wallet.page.nft.nftlist.nftWalletAddress
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.viewModelIOScope

private val TAG = SelectNFTViewModel::class.java.simpleName

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
    private var identifier: String? = null

    fun loadCollections() {
        viewModelIOScope(this) {
            val address = nftWalletAddress()
            val collectionResponse = if(isPreviewnet())
                service.getNFTCollections(address)
            else
                service.nftCollectionsOfAccount(address)
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
            this.identifier = collection.path.privatePath?.removePrefix("/private/") ?: ""
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
            this.identifier = ""
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
        this.identifier = detailInfo.identifier
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
            val nftListResponse = if (isPreviewnet()) {
                service.getNFTListOfCollection(
                    nftWalletAddress(), collectionId,
                    0, 20
                )
            } else {
                service.nftsOfCollection(
                    nftWalletAddress(), collectionId,
                    0, 20
                )
            }
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

    suspend fun moveSelectedNFT(toAddress: String, callback: (isSuccess: Boolean) -> Unit) {
        if (collectionContract == null || collectionAddress == null) {
            callback.invoke(false)
            return
        }
        if (WalletManager.isChildAccountSelected()) {
            if (EVMWalletManager.isEVMWalletAddress(toAddress)) {
                EVMWalletManager.moveNFTList(
                    collectionContract!!,
                    collectionAddress!!,
                    selectedNFTIdList,
                    isMoveToEVM = true,
                    callback
                )
            } else if (WalletManager.isChildAccount(toAddress)) {
                // batch move from child to child
                sendNFTListFromChildToChild(toAddress, selectedNFTIdList, callback)
            } else {
                // batch move from child to parent/child
                moveNFTListFromChildToParent(selectedNFTIdList, callback)
            }
        } else if (WalletManager.isEVMAccountSelected()) {
            // batch move from evm to parent
            EVMWalletManager.moveNFTList(
                collectionContract!!,
                collectionAddress!!,
                selectedNFTIdList,
                isMoveToEVM = false,
                callback
            )
        } else {
            if (EVMWalletManager.isEVMWalletAddress(toAddress)) {
                // batch move from parent to evm
                EVMWalletManager.moveNFTList(
                    collectionContract!!,
                    collectionAddress!!,
                    selectedNFTIdList,
                    isMoveToEVM = true,
                    callback
                )
            } else {
                // batch move from parent to child
                sendNFTListFromParentToChild(toAddress, selectedNFTIdList, callback)
            }
        }
    }

    private suspend fun moveNFTListFromChildToParent(
        idList: List<String>,
        callback: (isSuccess: Boolean) -> Unit
    ) {
        try {
            val collection = NftCollectionConfig.get(collectionAddress, collectionContract.orEmpty())
            if (collection == null) {
                callback.invoke(false)
                return
            }
            val txId = cadenceMoveNFTListFromChildToParent(
                WalletManager.selectedWalletAddress(), identifier.orEmpty(),
                collection, idList
            )
            if (txId.isNullOrBlank()) {
                logd(TAG, "move to parent failed")
                callback.invoke(false)
                return
            }
            TransactionStateWatcher(txId).watch { result ->
                if (result.isExecuteFinished()) {
                    logd(TAG, "move to parent success")
                    callback.invoke(true)
                } else if (result.isFailed()) {
                    logd(TAG, "move to parent failed")
                    callback.invoke(false)
                }
            }
        } catch (e: Exception) {
            callback.invoke(false)
            logd(TAG, "move to parent failed")
            e.printStackTrace()
        }
    }

    private suspend fun sendNFTListFromChildToChild(
        toAddress: String,
        idList: List<String>,
        callback: (isSuccess: Boolean) -> Unit
    ) {
        try {
            val collection = NftCollectionConfig.get(collectionAddress, collectionContract.orEmpty())
            if (collection == null) {
                callback.invoke(false)
                return
            }
            val txId = cadenceSendNFTListFromChildToChild(
                WalletManager.selectedWalletAddress(), toAddress, identifier.orEmpty(), collection, idList
            )
            if (txId.isNullOrBlank()) {
                logd(TAG, "send to child failed")
                callback.invoke(false)
                return
            }
            TransactionStateWatcher(txId).watch { result ->
                if (result.isExecuteFinished()) {
                    logd(TAG, "send to child success")
                    callback.invoke(true)

                } else if (result.isFailed()) {
                    logd(TAG, "send to child failed")
                    callback.invoke(false)
                }
            }
        } catch (e: Exception) {
            callback.invoke(false)
            logd(TAG, "send to child failed")
            e.printStackTrace()
        }
    }

    private suspend fun sendNFTListFromParentToChild(
        childAddress: String,
        idList: List<String>,
        callback: (isSuccess: Boolean) -> Unit
    ) {
        try {
            val collection = NftCollectionConfig.get(collectionAddress, collectionContract.orEmpty())
            if (collection == null) {
                callback.invoke(false)
                return
            }
            val txId = cadenceSendNFTListFromParentToChild(
                childAddress, identifier.orEmpty(), collection, idList
            )
            if (txId.isNullOrBlank()) {
                logd(TAG, "send to child failed")
                callback.invoke(false)
                return
            }
            TransactionStateWatcher(txId).watch { result ->
                if (result.isExecuteFinished()) {
                    logd(TAG, "send to child success")
                    callback.invoke(true)
                } else if (result.isFailed()) {
                    logd(TAG, "send to child failed")
                    callback.invoke(false)
                }
            }
        } catch (e: Exception) {
            callback.invoke(false)
            logd(TAG, "send to child failed")
            e.printStackTrace()
        }
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