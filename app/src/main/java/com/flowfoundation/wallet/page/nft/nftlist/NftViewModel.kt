package com.flowfoundation.wallet.page.nft.nftlist

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.account.OnWalletDataUpdate
import com.flowfoundation.wallet.manager.account.WalletFetcher
import com.flowfoundation.wallet.manager.config.NftCollection
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.network.model.NftCollectionWrapper
import com.flowfoundation.wallet.network.model.WalletListData
import com.flowfoundation.wallet.page.nft.nftlist.model.CollectionItemModel
import com.flowfoundation.wallet.page.nft.nftlist.model.CollectionTitleModel
import com.flowfoundation.wallet.page.nft.nftlist.model.NFTCountTitleModel
import com.flowfoundation.wallet.page.nft.nftlist.model.NFTItemModel
import com.flowfoundation.wallet.page.nft.nftlist.model.NftLoadMoreModel
import com.flowfoundation.wallet.page.nft.nftlist.utils.NftFavoriteManager
import com.flowfoundation.wallet.page.nft.nftlist.utils.NftGridRequester
import com.flowfoundation.wallet.page.nft.nftlist.utils.NftList
import com.flowfoundation.wallet.page.nft.nftlist.utils.NftListRequester
import com.flowfoundation.wallet.page.nft.nftlist.utils.OnNftFavoriteChangeListener
import com.flowfoundation.wallet.page.profile.subpage.wallet.ChildAccountCollectionManager
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.isNftCollectionExpanded
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.updateNftCollectionExpanded
import com.flowfoundation.wallet.utils.viewModelIOScope

private val TAG = NftViewModel::class.java.simpleName

class NftViewModel : ViewModel(), OnNftFavoriteChangeListener, OnWalletDataUpdate,
    OnTransactionStateChange {

    val collectionsLiveData = MutableLiveData<List<CollectionItemModel>>()
    val collectionTitleLiveData = MutableLiveData<CollectionTitleModel>()
    val listNftLiveData = MutableLiveData<List<Any>>()
    val collectionTabChangeLiveData = MutableLiveData<String>()
    val favoriteLiveData = MutableLiveData<List<Nft>>()
    val favoriteIndexLiveData = MutableLiveData<Int>()

    val gridNftLiveData = MutableLiveData<List<Any>>()

    val emptyLiveData = MutableLiveData<Boolean>()
    val listScrollChangeLiveData = MutableLiveData<Int>()

    private val gridRequester by lazy { NftGridRequester() }
    private val listRequester by lazy { NftListRequester() }

    private var selectedCollection: NftCollection? = null
    private var isCollectionExpanded = false

    val isGridViewLiveData = MutableLiveData<Boolean>().apply { value = false }

    init {
        NftFavoriteManager.addOnNftSelectionChangeListener(this)
        TransactionStateManager.addOnTransactionStateChange(this)
        observeWalletUpdate()
    }

    fun toggleViewType(isGridView: Boolean) {
        isGridViewLiveData.postValue(isGridView)
    }

    fun toggleCollectionExpand() {
        ioScope {
            updateNftCollectionExpanded(!isCollectionExpanded)
            isCollectionExpanded = isNftCollectionExpanded()
            requestList()
        }
    }

    fun requestChildAccountCollectionList() {
        ChildAccountCollectionManager.loadChildAccountNFTCollectionList()
    }

    fun requestList() {
        viewModelIOScope(this) {
            isCollectionExpanded = isNftCollectionExpanded()

            // read from cache
            val cacheCollections = listRequester.cacheCollections().orEmpty()
            notifyCollectionList(cacheCollections)
            updateDefaultSelectCollection()

            collectionTitleLiveData.postValue(CollectionTitleModel(count = cacheCollections.size))
            (selectedCollection ?: cacheCollections.firstOrNull()?.collection)?.let {
                logd(TAG, "notifyNftList 2")
                notifyNftList(it)
            }

            if (cacheCollections.isNotEmpty()) {
                emptyLiveData.postValue(false)
            }

            requestFavorite()

            // read from server
            val onlineCollections = listRequester.requestCollection().orEmpty()

            emptyLiveData.postValue(onlineCollections.isEmpty())

            collectionTitleLiveData.postValue(CollectionTitleModel(count = onlineCollections.size))
            notifyCollectionList(onlineCollections)
            updateDefaultSelectCollection()

            (selectedCollection ?: onlineCollections.firstOrNull()?.collection)?.let {
                listRequester.request(it)
                logd(TAG, "notifyNftList 1")
                notifyNftList(it)
            }
        }
    }

    override fun onNftFavoriteChange(nfts: List<Nft>) {
        favoriteLiveData.postValue(nfts)
    }

    private fun requestFavorite() {
        viewModelIOScope(this) { NftFavoriteManager.request() }
    }

    override fun onWalletDataUpdate(wallet: WalletListData) {
        refresh()
    }

    private fun observeWalletUpdate() {
        ioScope {
            // wallet not loaded yet
            if (nftWalletAddress().isEmpty()) {
                logd(TAG, "wallet not loaded yet")
                WalletFetcher.addListener(this)
            }
        }
    }

    private fun updateDefaultSelectCollection() {
        val cacheCollections = listRequester.cacheCollections()
        selectedCollection = selectedCollection ?: cacheCollections?.firstOrNull()?.collection
        if (selectedCollection != null && cacheCollections?.firstOrNull { it.collection?.contractName() == selectedCollection?.contractName() } == null) {
            cacheCollections?.firstOrNull()?.collection?.contractName()?.let { selectCollection(it) }
        }
    }

    fun requestGrid() {
        viewModelIOScope(this) {
            notifyGridList(gridRequester.cachedNfts())
            val nftList = gridRequester.request()
            notifyGridList(nftList)
            emptyLiveData.postValue(nftList.count == 0)
        }
    }

    fun requestListNextPage() {
        viewModelIOScope(this) {
            val collection = selectedCollection ?: return@viewModelIOScope
            listRequester.nextPage(collection)
            logd(TAG, "notifyNftList 3")
            notifyNftList(collection)
        }
    }

    fun requestGridNextPage() {
        viewModelIOScope(this) {
            notifyGridList(gridRequester.nextPage())
        }
    }

    fun updateSelectionIndex(position: Int) {
        favoriteIndexLiveData.value = position
    }

    fun selectCollection(contractName: String) {
        if (selectedCollection?.contractName() == contractName) {
            return
        }
        val collection = listRequester.cacheCollections()?.firstOrNull { it.collection?.contractName() == contractName } ?: return
        collectionTabChangeLiveData.postValue(contractName)
        selectedCollection = collection.collection
        viewModelIOScope(this) {
            val tmpCollection = selectedCollection ?: return@viewModelIOScope
            logd(TAG, "notifyNftList 4")
            notifyNftList(tmpCollection)

            listRequester.request(tmpCollection)
            logd(TAG, "notifyNftList 5")
            notifyNftList(tmpCollection)
        }
    }

    fun isCollectionExpanded() = isCollectionExpanded

    fun onListScrollChange(scrollY: Int) = apply { listScrollChangeLiveData.postValue(scrollY) }

    private fun notifyNftList(collection: NftCollection) {
        if (collection.contractName() != selectedCollection?.contractName()) {
            return
        }
        val list = mutableListOf<Any>().apply { addAll(listRequester.dataList(collection).map { NFTItemModel(nft = it) }) }
        if (list.isNotEmpty() && listRequester.haveMore()) {
            list.add(NftLoadMoreModel(isListLoadMore = true))
        }

        logd(TAG, "notifyNftList collection:${collection.name} size:${list.size}")

        if (list.isEmpty()) {
            val count = listRequester.cacheCollections()?.firstOrNull { it.collection?.contractName() == collection.contractName() }?.count ?: 0
            list.addAll(generateEmptyNftPlaceholders(count))
        }

        listNftLiveData.postValue(list)
    }

    private fun notifyCollectionList(collections: List<NftCollectionWrapper>?) {
        val selectedCollection = selectedCollection?.contractName() ?: collections?.firstOrNull()?.collection?.contractName()
        collectionsLiveData.postValue(collections.orEmpty().mapNotNull {
            val collection = it.collection ?: return@mapNotNull null
            CollectionItemModel(
                collection = collection,
                count = it.count ?: 0,
                isSelected = selectedCollection == collection.contractName()
            )
        })
    }

    private fun notifyGridList(nftList: NftList) {
        if (nftList.count == 0) {
            return
        }

        val list = mutableListOf<Any>(NFTCountTitleModel(nftList.count))

        list.addAll(gridRequester.dataList().map { NFTItemModel(nft = it) })
        if (gridRequester.haveMore()) {
            list.add(NftLoadMoreModel(isGridLoadMore = true))
        }
        gridNftLiveData.postValue(list)
    }

    fun refresh() {
        requestList()
        requestGrid()
        if (WalletManager.isEVMAccountSelected().not()) {
            requestChildAccountCollectionList()
        }
    }

    override fun onTransactionStateChange() {
        val transactionList = TransactionStateManager.getTransactionStateList()
        val transaction =
            transactionList.lastOrNull { it.type == TransactionState.TYPE_MOVE_NFT || it.type == TransactionState.TYPE_TRANSFER_NFT}
        transaction?.let { state ->
            if (state.isSuccess()) {
                refresh()
            }
        }
    }


}