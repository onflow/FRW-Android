package com.flowfoundation.wallet.page.nft.search.viewmodel

import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.page.nft.move.model.NFTInfo
import com.flowfoundation.wallet.page.nft.nftlist.cover
import com.flowfoundation.wallet.page.nft.nftlist.desc
import com.flowfoundation.wallet.page.nft.nftlist.title


class NFTInfoListViewModel: BaseNFTListViewModel<NFTInfo>() {

    override fun convertNFT(nft: Nft): NFTInfo {
        return NFTInfo(
            id = nft.id,
            title = nft.title().orEmpty(),
            cover = nft.cover().orEmpty(),
            description = nft.desc().orEmpty(),
            traits = nft.traits?.map { it.value } ?: emptyList()
        )
    }

    override fun getSearchableText(item: NFTInfo): String {
        val titleAndDesc = "${item.title} ${item.description}"
        val traits = item.traits.joinToString(" ") { it }
        return "$titleAndDesc $traits"
    }
}