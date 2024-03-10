package com.flowfoundation.wallet.page.inbox

import com.flowfoundation.wallet.network.model.InboxNft
import com.flowfoundation.wallet.network.model.InboxResponse
import com.flowfoundation.wallet.network.model.InboxToken
import com.flowfoundation.wallet.utils.getInboxReadList
import com.flowfoundation.wallet.utils.updateInboxReadListPref

suspend fun updateInboxReadList(inboxResponse: InboxResponse) {
    updateInboxReadListPref(inboxResponse.toReadList().joinToString(",") { it })
}

suspend fun countUnreadInbox(inboxResponse: InboxResponse): Int {
    val cacheReadList = getInboxReadList()
    val remoteList = inboxResponse.toReadList().toMutableList()
    remoteList.removeAll { cacheReadList.contains(it) }
    return remoteList.size
}

private fun InboxResponse.toReadList(): List<String> {
    val data = mutableListOf<String>()
    data.addAll(tokenList().map { it.tag() })
    data.addAll(nftList().map { it.tag() })
    return data
}

private fun InboxToken.tag() = "${coinSymbol}-${amount}"
private fun InboxNft.tag() = "${collectionAddress}-${tokenId}"