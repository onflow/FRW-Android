package com.flowfoundation.wallet.page.nft.nftdetail

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.page.nft.nftlist.nftWalletAddress
import com.flowfoundation.wallet.page.nft.nftlist.utils.NftCache
import com.flowfoundation.wallet.utils.viewModelIOScope

class NftDetailViewModel : ViewModel() {

    val nftLiveData = MutableLiveData<Nft>()

    fun load(uniqueId: String, collectionContractId: String?, collectionContract: String?) {
        viewModelIOScope(this) {
            val walletAddress = nftWalletAddress()
            val nft = NftCache(walletAddress).findNFTByIdAndContractName(uniqueId, collectionContractId.orEmpty(), collectionContract.orEmpty())

            nft?.let {
                nftLiveData.postValue(it)
                requestMeta(walletAddress, it)
            }
        }
    }

    private suspend fun requestMeta(walletAddress: String, nft: Nft) {
//        val service = retrofit().create(ApiService::class.java)
//        val resp = service.nftMeta(walletAddress, nft.contract.name.orEmpty(), nft.id.tokenId)
    }
}