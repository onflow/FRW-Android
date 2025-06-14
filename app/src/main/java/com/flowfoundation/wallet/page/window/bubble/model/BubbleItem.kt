package com.flowfoundation.wallet.page.window.bubble.model

import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionState.Companion.TYPE_ADD_TOKEN
import com.flowfoundation.wallet.manager.transaction.TransactionState.Companion.TYPE_ENABLE_NFT
import com.flowfoundation.wallet.manager.transaction.TransactionState.Companion.TYPE_FCL_TRANSACTION
import com.flowfoundation.wallet.manager.transaction.TransactionState.Companion.TYPE_NFT
import com.flowfoundation.wallet.manager.transaction.TransactionState.Companion.TYPE_TRANSFER_COIN
import com.flowfoundation.wallet.manager.transaction.TransactionState.Companion.TYPE_TRANSFER_NFT
import com.flowfoundation.wallet.page.browser.toFavIcon
import com.flowfoundation.wallet.page.browser.tools.BrowserTab
import com.flowfoundation.wallet.page.nft.nftlist.getNFTCover
import com.flowfoundation.wallet.utils.extensions.res2String

class BubbleItem(
    val data: Any,
)

fun BubbleItem.icon(): Any? {
    return when (data) {
        is BrowserTab -> data.url()?.toFavIcon()
        is TransactionState -> data.icon()
        else -> R.mipmap.ic_launcher_round
    }
}

fun BubbleItem.title(): String {
    return when (data) {
        is BrowserTab -> data.title().orEmpty()
        is TransactionState -> data.title()
        else -> ""
    }
}

private fun TransactionState.icon(): Any {
    return when (type) {
        TYPE_NFT -> nftData().nft.getNFTCover() ?: ""
        TYPE_TRANSFER_COIN -> FungibleTokenListManager.getTokenById(coinData().coinId)?.tokenIcon().orEmpty()
        TYPE_ADD_TOKEN -> tokenData()?.icon().orEmpty()
        TYPE_ENABLE_NFT -> nftCollectionData()?.logo().orEmpty()
        TYPE_TRANSFER_NFT -> nftSendData().nft.getNFTCover() ?: ""
        TYPE_FCL_TRANSACTION -> fclTransactionData().url?.toFavIcon().orEmpty()
        else -> R.mipmap.ic_launcher_round
    }
}

private fun TransactionState.title(): String {
    return R.string.pending_transaction.res2String()
}