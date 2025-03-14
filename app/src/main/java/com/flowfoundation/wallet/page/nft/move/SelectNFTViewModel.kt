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
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.viewModelIOScope

private val TAG = SelectNFTViewModel::class.java.simpleName

class SelectNFTViewModel : ViewModel() {

    companion object {
        const val SELECT_NFT_LIMIT = 10
    }

    val collectionLiveData = MutableLiveData<CollectionInfo?>()
    val moveCountLiveData = MutableLiveData<Int>()
    private val selectedNFTIdList = mutableListOf<String>()
    private val service by lazy { retrofitApi().create(ApiService::class.java) }
    private var identifier: String? = null
    private var nftIdentifier: String? = null


    fun loadCollections(fromAddress: String) {
        viewModelIOScope(this) {
            val collectionResponse = if (EVMWalletManager.isEVMWalletAddress(fromAddress)) {
                service.getEVMNFTCollections(fromAddress)
            } else {
                service.getNFTCollections(fromAddress)
            }
            if (collectionResponse.data.isNullOrEmpty()) {
                postEmpty()
                return@viewModelIOScope
            }
            val collection = collectionResponse.data.firstOrNull { it.collection != null }?.collection
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
        }
    }



    private fun postEmpty() {
        collectionLiveData.postValue(null)
        selectedNFTIdList.clear()
    }

    fun setCollectionInfo(fromAddress: String, detailInfo: CollectionDetailInfo) {
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
    }

    suspend fun moveSelectedNFT(fromAddress: String, toAddress: String, callback: (isSuccess: Boolean) -> Unit) {

        logd(TAG, "moveSelectedNFT called with: fromAddress = ${fromAddress}, toAddress = $toAddress, nftIdentifier = $nftIdentifier, selectedNFTs = $selectedNFTIdList")

        if (nftIdentifier == null) {
            callback.invoke(false)
            return
        }
        if (WalletManager.isChildAccount(fromAddress)) {
            logd(TAG, "moveSelectedNFT from child account")
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
                sendNFTListFromChildToChild(fromAddress, toAddress, selectedNFTIdList, callback)
            } else {
                // batch move from child to parent/child
                moveNFTListFromChildToParent(fromAddress, selectedNFTIdList, callback)
            }
        } else if (EVMWalletManager.isEVMWalletAddress(fromAddress)) {
            logd(TAG, "moveSelectedNFT from EVM account")
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
        fromAddress: String,
        idList: List<String>,
        callback: (isSuccess: Boolean) -> Unit
    ) {
        executeTransaction(
            action = {
                val collection = NftCollectionConfig.getByNFTIdentifier(nftIdentifier.orEmpty())?: return@executeTransaction null
                val txId = cadenceMoveNFTListFromChildToParent(
                    fromAddress, identifier.orEmpty(),
                    collection, idList
                )
                MixpanelManager.transferNFT(
                    fromAddress, WalletManager.wallet()?.walletAddress().orEmpty(),
                    nftIdentifier.orEmpty(), txId.orEmpty(), TransferAccountType.CHILD,
                    TransferAccountType.FLOW, true
                )
                txId
            },
            operationName = "move to parent",
            callback = callback
        )
    }

    private suspend fun sendNFTListFromChildToChild(
        fromAddress: String,
        toAddress: String,
        idList: List<String>,
        callback: (isSuccess: Boolean) -> Unit
    ) {
        executeTransaction(
            action = {
                val collection = NftCollectionConfig.getByNFTIdentifier(nftIdentifier.orEmpty()) ?: return@executeTransaction null
                val txId = cadenceSendNFTListFromChildToChild(
                    fromAddress, toAddress, identifier.orEmpty(), collection, idList
                )
                MixpanelManager.transferNFT(
                    fromAddress, toAddress,
                    nftIdentifier.orEmpty(), txId.orEmpty(), TransferAccountType.CHILD,
                    TransferAccountType.CHILD, true
                )
                txId
            },
            operationName = "send to child",
            callback = callback
        )
    }

    private suspend fun sendNFTListFromParentToChild(
        childAddress: String,
        idList: List<String>,
        callback: (isSuccess: Boolean) -> Unit
    ) {
        executeTransaction(
            action = {
                val collection = NftCollectionConfig.getByNFTIdentifier(nftIdentifier.orEmpty()) ?: return@executeTransaction null
                val txId = cadenceSendNFTListFromParentToChild(
                    childAddress, identifier.orEmpty(), collection, idList
                )
                MixpanelManager.transferNFT(
                    WalletManager.wallet()?.walletAddress().orEmpty(), childAddress,
                    nftIdentifier.orEmpty(), txId.orEmpty(), TransferAccountType.FLOW,
                    TransferAccountType.CHILD, true
                )
                txId
            },
            operationName = "send to child",
            callback = callback
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

    private suspend inline fun executeTransaction(
        crossinline action: suspend () -> String?,
        operationName: String,
        crossinline callback: (Boolean) -> Unit
    ) {
        try {
            val txId = action()
            if (txId.isNullOrBlank()) {
                logd(TAG, "$operationName failed")
                callback(false)
                return
            }

            TransactionStateWatcher(txId).watch { result ->
                when {
                    result.isExecuteFinished() -> {
                        logd(TAG, "$operationName success")
                        callback(true)
                    }
                    result.isFailed() -> {
                        logd(TAG, "$operationName failed")
                        callback(false)
                    }
                }
            }
        } catch (e: Exception) {
            logd(TAG, "$operationName failed :: ${e.message}")
            callback(false)
        }
    }

}