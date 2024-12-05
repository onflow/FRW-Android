package com.flowfoundation.wallet.page.nft.move

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.config.NftCollectionConfig
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceMoveNFTListFromChildToParent
import com.flowfoundation.wallet.manager.flowjvm.cadenceSendNFTListFromChildToChild
import com.flowfoundation.wallet.manager.flowjvm.cadenceSendNFTListFromParentToChild
import com.flowfoundation.wallet.manager.transaction.TransactionStateWatcher
import com.flowfoundation.wallet.manager.transaction.isExecuteFinished
import com.flowfoundation.wallet.manager.transaction.isFailed
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.mixpanel.TransferAccountType
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
    private var identifier: String? = null
    private var nftIdentifier: String? = null

    fun loadCollections() {
        viewModelIOScope(this) {
            val address = nftWalletAddress()
            val collectionResponse = if (EVMWalletManager.isEVMWalletAddress(address)) {
                service.getEVMNFTCollections(address)
            } else {
                service.getNFTCollections(address)
            }
            if (collectionResponse.data.isNullOrEmpty()) {
                postEmpty()
                return@viewModelIOScope
            }
            val collection =collectionResponse.data.firstOrNull { it.collection != null }?.collection

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
            this.nftIdentifier = collection.getNFTIdentifier()
            this.identifier = collection.path?.privatePath?.removePrefix("/private/") ?: ""
            loadNFTList(collection.id)
        }
    }

    private fun postEmpty() {
        collectionLiveData.postValue(null)
        nftListLiveData.postValue(emptyList())
        selectedNFTIdList.clear()
    }

    fun setCollectionInfo(detailInfo: CollectionDetailInfo) {
        this.nftIdentifier = detailInfo.nftIdentifier
        this.identifier = detailInfo.identifier
        collectionLiveData.postValue(
            CollectionInfo(
                id = detailInfo.id,
                name = detailInfo.name,
                logo = detailInfo.logo
            )
        )
        selectedNFTIdList.clear()
        loadNFTList(detailInfo.id)
    }

    private fun loadNFTList(collectionId: String) {
        viewModelIOScope(this) {
            val nftListResponse = if (WalletManager.isEVMAccountSelected()) {
                service.getEVMNFTListOfCollection(
                    nftWalletAddress(), collectionId, "", 40
                )
            } else {
                service.getNFTListOfCollection(
                    nftWalletAddress(), collectionId, 0, 40)
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
        if (nftIdentifier == null) {
            callback.invoke(false)
            return
        }
        if (WalletManager.isChildAccountSelected()) {
            if (EVMWalletManager.isEVMWalletAddress(toAddress)) {
                EVMWalletManager.moveChildNFTList(
                    nftIdentifier!!,
                    selectedNFTIdList,
                    WalletManager.selectedWalletAddress(),
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
            if (WalletManager.isChildAccount(toAddress)) {
                EVMWalletManager.moveChildNFTList(
                    nftIdentifier!!,
                    selectedNFTIdList,
                    toAddress,
                    isMoveToEVM = false,
                    callback
                )
            } else {
                // batch move from evm to parent
                EVMWalletManager.moveNFTList(
                    nftIdentifier!!,
                    selectedNFTIdList,
                    isMoveToEVM = false,
                    callback
                )
            }
        } else {
            if (EVMWalletManager.isEVMWalletAddress(toAddress)) {
                // batch move from parent to evm
                EVMWalletManager.moveNFTList(
                    nftIdentifier!!,
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
            val collection = NftCollectionConfig.getByNFTIdentifier(nftIdentifier.orEmpty())
            if (collection == null) {
                callback.invoke(false)
                return
            }
            val txId = cadenceMoveNFTListFromChildToParent(
                WalletManager.selectedWalletAddress(), identifier.orEmpty(),
                collection, idList
            )
            MixpanelManager.transferNFT(
                WalletManager.selectedWalletAddress(), WalletManager.wallet()?.walletAddress().orEmpty(),
                nftIdentifier.orEmpty(), txId.orEmpty(), TransferAccountType.CHILD,
                TransferAccountType.FLOW, true
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
            val collection = NftCollectionConfig.getByNFTIdentifier(nftIdentifier.orEmpty())
            if (collection == null) {
                callback.invoke(false)
                return
            }
            val txId = cadenceSendNFTListFromChildToChild(
                WalletManager.selectedWalletAddress(), toAddress, identifier.orEmpty(), collection, idList
            )
            MixpanelManager.transferNFT(
                WalletManager.selectedWalletAddress(), toAddress,
                nftIdentifier.orEmpty(), txId.orEmpty(), TransferAccountType.CHILD,
                TransferAccountType.CHILD, true
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
            val collection = NftCollectionConfig.getByNFTIdentifier(nftIdentifier.orEmpty())
            if (collection == null) {
                callback.invoke(false)
                return
            }
            val txId = cadenceSendNFTListFromParentToChild(
                childAddress, identifier.orEmpty(), collection, idList
            )
            MixpanelManager.transferNFT(
                WalletManager.wallet()?.walletAddress().orEmpty(), childAddress,
                nftIdentifier.orEmpty(), txId.orEmpty(), TransferAccountType.FLOW,
                TransferAccountType.CHILD, true
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