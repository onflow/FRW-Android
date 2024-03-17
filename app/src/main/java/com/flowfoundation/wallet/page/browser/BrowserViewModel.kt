package com.flowfoundation.wallet.page.browser

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.flowfoundation.wallet.page.browser.model.DockDuckGoRecommend
import com.flowfoundation.wallet.page.browser.model.RecommendModel
import com.flowfoundation.wallet.page.browser.tools.BrowserTab
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.uiScope
import java.net.URL
import java.net.URLEncoder

class BrowserViewModel {
    internal var onUrlUpdateLiveData: ((String) -> Unit)? = null

    internal var onHideInputPanel: (() -> Unit)? = null

    internal var recommendWordsLiveData: ((List<RecommendModel>) -> Unit)? = null

    internal var onRemoveBrowserTab: ((BrowserTab) -> Unit)? = null

    internal var onTabChange: (() -> Unit)? = null

    internal var onSearchBoxHide: (() -> Unit)? = null

    private var searchKeyword = ""

    fun updateUrl(url: String) {
        onUrlUpdateLiveData?.invoke(url)
    }

    fun queryRecommendWord(keyword: String) {
        this.searchKeyword = keyword
        ioScope {
            runCatching {
                if (keyword.isBlank()) {
                    uiScope { recommendWordsLiveData?.invoke(emptyList()) }
                    return@ioScope
                }

                val list = queryRecommendWordInternal(keyword)
                if (searchKeyword == keyword) {
                    uiScope { recommendWordsLiveData?.invoke(list.map { RecommendModel(it, keyword, this@BrowserViewModel) }) }
                }
            }
        }
    }

    private fun queryRecommendWordInternal(keyword: String): List<String> {
        val json = URL("https://ac.duckduckgo.com/ac?q=${URLEncoder.encode(keyword)}&type=json").openStream().bufferedReader().use { it.readText() }
        return Gson().fromJson<List<DockDuckGoRecommend>>(json, object : TypeToken<List<DockDuckGoRecommend>>() {}.type).map { it.phrase }
    }

    fun hideInputPanel() {
        onHideInputPanel?.invoke()
    }

    fun onTabChange() {
        onTabChange?.invoke()
    }

    fun onSearchBoxHide() {
        onSearchBoxHide?.invoke()
    }
}