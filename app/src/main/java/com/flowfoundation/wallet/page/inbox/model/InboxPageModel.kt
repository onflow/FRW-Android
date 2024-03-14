package com.flowfoundation.wallet.page.inbox.model

import com.flowfoundation.wallet.network.model.InboxNft
import com.flowfoundation.wallet.network.model.InboxToken

class InboxPageModel(
    val tokenList: List<InboxToken>? = null,
    val nftList: List<InboxNft>? = null,
    val claimExecuting: Boolean? = null,
)