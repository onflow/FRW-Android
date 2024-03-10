package com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail.model

import com.flowfoundation.wallet.manager.childaccount.ChildAccount
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail.CoinData
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail.CollectionData

class ChildAccountDetailModel(
    val account: ChildAccount? = null,
    val nftCollections: List<CollectionData>? = null,
    val coinList: List<CoinData>? = null,
)