package com.flowfoundation.wallet.page.nft.search.viewmodel

import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.page.nft.nftlist.desc
import com.flowfoundation.wallet.page.nft.nftlist.model.NFTItemModel
import com.flowfoundation.wallet.page.nft.nftlist.title


class NFTItemListViewModel : BaseNFTListViewModel<NFTItemModel>() {
    override fun convertNFT(nft: Nft): NFTItemModel {
        return NFTItemModel(
            nft = nft,
            accountAddress = currentFromAddress
        )
    }

    override fun getSearchableText(item: NFTItemModel): String {
        val titleAndDesc = "${item.nft.title()} ${item.nft.desc()}"
        val traits = item.nft.traits?.joinToString(" ") { it.value } ?: ""
        return "$titleAndDesc $traits"
    }

}