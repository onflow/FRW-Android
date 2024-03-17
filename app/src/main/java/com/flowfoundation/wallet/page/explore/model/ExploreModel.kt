package com.flowfoundation.wallet.page.explore.model

import com.flowfoundation.wallet.database.Bookmark
import com.flowfoundation.wallet.database.WebviewRecord

class ExploreModel(
    val recentList: List<WebviewRecord>? = null,
    val bookmarkList: List<Bookmark>? = null,
    val dAppList: List<DAppModel>? = null,
    val dAppTagList: List<DAppTagModel>? = null,
)