package com.flowfoundation.wallet.page.browser.model

import android.graphics.Point

class BrowserInputModel(
    val onLoadNewUrl: String? = null,
    val onHideInputPanel: Boolean? = null,
    val onPageAttach: Boolean? = null,
    val onPageDetach: Boolean? = null,
    val recommendWords: List<RecommendModel>? = null,
    val searchBoxPosition: Point? = null,
)