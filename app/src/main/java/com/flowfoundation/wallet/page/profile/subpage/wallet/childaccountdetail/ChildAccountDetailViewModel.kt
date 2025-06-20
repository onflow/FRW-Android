package com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.childaccount.ChildAccount
import com.flowfoundation.wallet.manager.config.NftCollectionConfig
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.retrofitApi
import com.flowfoundation.wallet.utils.viewModelIOScope

class ChildAccountDetailViewModel : ViewModel() {
    val nftCollectionsLiveData = MutableLiveData<List<CollectionData>>()
    val coinListLiveData = MutableLiveData<List<CoinData>>()

    private val service by lazy { retrofitApi().create(ApiService::class.java) }

    // id format A.a60698727837eccf.GamePieceNFT.Collection
    fun queryCoinList(account: ChildAccount) {
        viewModelIOScope(this) {
            val tokenList = queryChildAccountTokens(account.address)
            val coinDataList = mutableListOf<CoinData>()
            tokenList.forEach {
                val idSplitList = it.id.split(".", ignoreCase = true, limit = 0)
                val contractName = idSplitList[2]
                val address = idSplitList[1]
                val token = FungibleTokenListManager.getFungibleToken { flowCoin ->
                    contractName == flowCoin.tokenContractName() && address == flowCoin.tokenAddress() }
                coinDataList.add(
                    CoinData(
                        token?.name ?: contractName,
                                token?.tokenIcon().orEmpty().ifBlank {
                            "https://lilico.app/placeholder-2.0.png"
                        },
                        token?.symbol.orEmpty(),
                        it.balance
                    )
                )
            }
            coinListLiveData.postValue(coinDataList)
        }
    }

    fun queryCollections(account: ChildAccount) {
        viewModelIOScope(this) {
            val collectionIds = queryChildAccountNFTCollectionID(account.address)
            val collectionList = mutableListOf<CollectionData>()
            val nftCollection = service.getNFTCollections(account.address)
            collectionIds.forEach { contractId ->
                val collectionWrapper = nftCollection.data?.firstOrNull { it.collection?.contractIdWithCollection() == contractId }
                val collection = collectionWrapper?.collection ?: NftCollectionConfig.getByContractId(contractId)
                val contractName = contractId.split(".", ignoreCase = true, limit = 0)[2]
                collectionList.add(
                    CollectionData(
                        contractId,
                        collection?.name ?: contractName,
                        collection?.logo() ?: "",
                        account.address,
                        collection?.contractName() ?: contractName,
                        collectionWrapper?.ids ?: emptyList()
                    )
                )
            }
            nftCollectionsLiveData.postValue(collectionList)
        }
    }

}