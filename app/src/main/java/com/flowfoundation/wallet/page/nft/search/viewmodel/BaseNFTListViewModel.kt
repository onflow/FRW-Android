package com.flowfoundation.wallet.page.nft.search.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.network.retrofitApi
import com.flowfoundation.wallet.page.nft.search.model.NFTListType
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.viewModelIOScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope


abstract class BaseNFTListViewModel<T> : ViewModel() {
    companion object {
        private val TAG = BaseNFTListViewModel::class.java.simpleName
        const val NFT_PAGE_SIZE = 50
    }

    val nftListLiveData = MutableLiveData<List<T>?>()

    val loadingProgressLiveData = MutableLiveData<Pair<Int, Int>>()

    val isLoadingLiveData = MutableLiveData<Boolean>()

    val listTypeLiveData = MutableLiveData<NFTListType>()

    private val allNftList = mutableListOf<T>()
    protected val service: ApiService by lazy { retrofitApi().create(ApiService::class.java) }
    private var isLoadingAll = false
    private var totalNftCount = 0
    protected var currentFromAddress = ""
    private var currentCollectionId = ""

    protected abstract fun convertNFT(nft: Nft): T
    protected abstract fun getSearchableText(item: T): String

    fun loadAllNFTs(fromAddress: String, collectionId: String) {
        if (isLoadingAll) return

        currentFromAddress = fromAddress
        currentCollectionId = collectionId

        viewModelIOScope(this) {
            isLoadingAll = true
            loadingProgressLiveData.postValue(Pair(0, 0))
            isLoadingLiveData.postValue(true)
            allNftList.clear()

            val isEvmAddress = EVMWalletManager.isEVMWalletAddress(fromAddress)
            try {
                if (isEvmAddress) {
                    var offset = ""
                    var hasMore = true
                    while (hasMore) {
                        val response = service.getEVMNFTListOfCollection(
                            fromAddress,
                            collectionId,
                            offset,
                            NFT_PAGE_SIZE
                        )
                        val pageList = response.data?.nfts?.map {
                            convertNFT(nft = it)
                        }?.toList() ?: emptyList()

                        if (pageList.isEmpty()) {
                            hasMore = false
                        } else {
                            allNftList.addAll(pageList)

                            totalNftCount = response.data?.nftCount ?: totalNftCount

                            loadingProgressLiveData.postValue(Pair(allNftList.size, totalNftCount))

                            if (allNftList.size >= totalNftCount) {
                                hasMore = false
                            }
                            offset = response.data?.offset ?: ""
                        }
                    }
                } else {
                    val firstPageResponse = service.getNFTListOfCollection(fromAddress, collectionId, 0, NFT_PAGE_SIZE)
                    val firstPageList = firstPageResponse.data?.nfts?.map {
                        convertNFT(nft = it)
                    }?.toList() ?: emptyList()

                    allNftList.addAll(firstPageList)
                    totalNftCount = firstPageResponse.data?.nftCount ?: 0
                    loadingProgressLiveData.postValue(Pair(allNftList.size, totalNftCount))

                    if (totalNftCount <= NFT_PAGE_SIZE) {
                        nftListLiveData.postValue(allNftList.toList())
                        listTypeLiveData.postValue(NFTListType.ALL)
                        isLoadingAll = false
                        isLoadingLiveData.postValue(false)
                        return@viewModelIOScope
                    }

                    val remainingCount = totalNftCount - allNftList.size
                    val pageCount = (remainingCount + NFT_PAGE_SIZE - 1) / NFT_PAGE_SIZE
                    val maxConcurrent = 10

                    coroutineScope {
                        for (batch in 0 until pageCount step maxConcurrent) {
                            val batchEnd = minOf(batch + maxConcurrent, pageCount)
                            val batchDeferred = mutableListOf<Deferred<List<T>>>()

                            for (page in batch until batchEnd) {
                                val offset = NFT_PAGE_SIZE + page * NFT_PAGE_SIZE
                                val deferred = async {
                                    try {
                                        val response = service.getNFTListOfCollection(fromAddress, collectionId, offset, NFT_PAGE_SIZE)
                                        val nftList = response.data?.nfts?.map {
                                            convertNFT(nft = it)
                                        }?.toList() ?: emptyList()

                                        synchronized(allNftList) {
                                            allNftList.addAll(nftList)
                                            loadingProgressLiveData.postValue(Pair(allNftList.size, totalNftCount))
                                        }

                                        nftList
                                    } catch (e: Exception) {
                                        logd(TAG, "Failed to load page at offset $offset: ${e.message}")
                                        emptyList()
                                    }
                                }
                                batchDeferred.add(deferred)
                            }
                            batchDeferred.awaitAll()
                        }
                    }
                }

                nftListLiveData.postValue(if (allNftList.isEmpty()) emptyList() else allNftList.toList())
                listTypeLiveData.postValue(NFTListType.ALL)
            } catch (e: Exception) {
                logd(TAG, "Load all NFTs failed: ${e.message}")
                e.printStackTrace()
                nftListLiveData.postValue(null)
                listTypeLiveData.postValue(NFTListType.ALL)
            } finally {
                isLoadingAll = false
                isLoadingLiveData.postValue(false)
            }
        }
    }

    fun retry() {
        if (currentFromAddress.isNotEmpty() && currentCollectionId.isNotEmpty()) {
            loadAllNFTs(currentFromAddress, currentCollectionId)
        }
    }

    fun searchNFT(keyword: String) {
        viewModelIOScope(this) {
            if (nftListLiveData.value == null) {
                return@viewModelIOScope
            }

            if (keyword.isBlank()) {
                nftListLiveData.postValue(allNftList.toList())
                listTypeLiveData.postValue(NFTListType.ALL)
            } else {
                val filteredList = allNftList.filter {
                    getSearchableText(it).contains(keyword, ignoreCase = true)
                }
                if (filteredList.isNotEmpty()) {
                    nftListLiveData.postValue(filteredList)
                    listTypeLiveData.postValue(NFTListType.RESULTS)
                }
            }
        }
    }
}