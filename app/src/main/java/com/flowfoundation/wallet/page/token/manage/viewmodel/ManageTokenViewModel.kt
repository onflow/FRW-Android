package com.flowfoundation.wallet.page.token.manage.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.manager.token.FungibleTokenListUpdateListener
import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.flowfoundation.wallet.utils.viewModelIOScope


class ManageTokenViewModel: ViewModel(), FungibleTokenListUpdateListener {
    val tokenListLiveData = MutableLiveData<List<FungibleToken>>()
    val tokenIndexLiveData = MutableLiveData<Int>()

    private val tokenList = mutableListOf<FungibleToken>()

    private var keyword = ""

    init {
        FungibleTokenListManager.addTokenListUpdateListener(this)
    }

    fun load() {
        viewModelIOScope(this) {
            tokenList.clear()
            tokenList.addAll(FungibleTokenListManager.getCurrentTokenListSnapshot())
            tokenListLiveData.postValue(tokenList)
            
            // If no tokens are available, trigger a reload
            if (tokenList.isEmpty()) {
                FungibleTokenListManager.reload()
            }
        }
    }

    fun search(keyword: String) {
        this.keyword = keyword
        if (keyword.isBlank()) {
            tokenListLiveData.postValue(tokenList)
        } else {
            val filteredList = tokenList.filter {
                it.name.contains(keyword, true) || it.symbol.contains(keyword, true)
            }
            tokenListLiveData.postValue(filteredList)
        }
    }

    fun clearSearch() {
        search("")
    }

    override fun onTokenListUpdated(list: List<FungibleToken>) {
        tokenList.clear()
        tokenList.addAll(FungibleTokenListManager.getCurrentTokenListSnapshot())
        search(keyword)
    }

    override fun onTokenDisplayUpdated(token: FungibleToken, isAdd: Boolean) {
        val index = tokenListLiveData.value?.indexOf(token) ?: -1
        tokenIndexLiveData.postValue(index)
    }
}