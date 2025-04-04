package com.flowfoundation.wallet.page.inbox

import com.flowfoundation.wallet.network.model.InboxNft
import com.flowfoundation.wallet.network.model.InboxResponse
import com.flowfoundation.wallet.network.model.InboxToken
import com.flowfoundation.wallet.utils.updateInboxReadListPref

suspend fun updateInboxReadList(inboxResponse: InboxResponse) {
    updateInboxReadListPref(inboxResponse.toReadList().joinToString(",") { it })
}

private fun InboxResponse.toReadList(): List<String> {
    val data = mutableListOf<String>()
    data.addAll(tokenList().map { it.tag() })
    data.addAll(nftList().map { it.tag() })
    return data
}

private fun InboxToken.tag() = "${coinSymbol}-${amount}"
private fun InboxNft.tag() = "${collectionAddress}-${tokenId}"