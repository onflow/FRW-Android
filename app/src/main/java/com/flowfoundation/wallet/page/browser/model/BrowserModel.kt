package com.flowfoundation.wallet.page.browser.model

import com.flowfoundation.wallet.page.browser.BrowserParams
import com.flowfoundation.wallet.page.browser.tools.BrowserTab

class BrowserModel(
    val url: String? = null,
    val onPageClose: Boolean? = null,
    val removeTab: BrowserTab? = null,
    val onTabChange: Boolean? = null,
    val params: BrowserParams? = null,
)