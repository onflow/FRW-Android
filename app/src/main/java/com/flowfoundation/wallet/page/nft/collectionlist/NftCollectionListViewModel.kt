package com.flowfoundation.wallet.page.nft.collectionlist

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import org.onflow.flow.sdk.FlowTransactionStatus
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.config.NftCollection
import com.flowfoundation.wallet.manager.config.NftCollectionConfig
import com.flowfoundation.wallet.manager.flowjvm.cadenceNftEnabled
import com.flowfoundation.wallet.manager.nft.NftCollectionStateChangeListener
import com.flowfoundation.wallet.manager.nft.NftCollectionStateManager
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.page.nft.collectionlist.model.NftCollectionItem
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.viewModelIOScope

class NftCollectionListViewModel : ViewModel(), OnTransactionStateChange, NftCollectionStateChangeListener {

    val collectionListLiveData = MutableLiveData<List<NftCollectionItem>>()
    var cadenceExecuteLiveData = MutableLiveData<Boolean>()

    private val collectionList = mutableListOf<NftCollectionItem>()

    private var transactionIds = mutableListOf<String>()

    private var keyword = ""

    init {
        TransactionStateManager.addOnTransactionStateChange(this)
        NftCollectionStateManager.addListener(this)
    }

    fun load() {
        viewModelIOScope(this) {
            collectionList.clear()
            collectionList.addAll(
                NftCollectionConfig.list()
                    .filter { it.contractId().isNotEmpty() }
                    .map { NftCollectionItem(collection = it, isAdded = NftCollectionStateManager.isTokenAdded(it.contractId()), isAdding = false) })
            collectionListLiveData.postValue(collectionList.toList())

            onTransactionStateChange()

            NftCollectionStateManager.fetchState()
        }
    }

    fun search(keyword: String) {
        this.keyword = keyword
        if (keyword.isBlank()) {
            collectionListLiveData.postValue(collectionList.toList())
        } else {
            collectionListLiveData.postValue(collectionList.filter { it.collection.name.lowercase().contains(keyword.lowercase()) })
        }
    }

    fun clearSearch() {
        this.keyword = ""
        search("")
    }

    fun addToken(collection: NftCollection) {
        ioScope {
            val transactionId = cadenceNftEnabled(collection)
            if (transactionId.isNullOrBlank()) {
                toast(msgRes = R.string.add_token_failed)
            } else {
                val transactionState = TransactionState(
                    transactionId = transactionId,
                    time = System.currentTimeMillis(),
                    state = FlowTransactionStatus.PENDING.num,
                    type = TransactionState.TYPE_ENABLE_NFT,
                    data = Gson().toJson(collection)
                )
                TransactionStateManager.newTransaction(transactionState)
                pushBubbleStack(transactionState)
                transactionIds.add(transactionId)
                onTransactionStateChange()
            }
            cadenceExecuteLiveData.postValue(true)
        }
    }

    override fun onTransactionStateChange() {
        viewModelIOScope(this) {
            val transactionList = TransactionStateManager.getTransactionStateList()
            transactionList.forEach { state ->
                if (state.type == TransactionState.TYPE_ENABLE_NFT) {
                    val coin = state.nftCollectionData()
                    val index = collectionList.indexOfFirst { it.collection.contractId() == coin.contractId() }
                    val isAdded = NftCollectionStateManager.isTokenAdded(coin.contractId())
                    collectionList[index] = NftCollectionItem(
                        collection = collectionList[index].collection,
                        isAdding = !state.isFailed() && !isAdded,
                        isAdded = isAdded,
                    )
                }
            }
            search(keyword)
        }
    }

    override fun onNftCollectionStateChange(collection: NftCollection, isEnable: Boolean) {
        onTransactionStateChange()
    }
}
