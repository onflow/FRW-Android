package com.flowfoundation.wallet.page.token.addtoken

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.nftco.flow.sdk.FlowTransactionStatus
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.flowjvm.cadenceEnableToken
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.manager.token.FungibleTokenListUpdateListener
import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.TokenInfo
import com.flowfoundation.wallet.network.retrofitApi
import com.flowfoundation.wallet.page.token.addtoken.model.TokenItem
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.viewModelIOScope

class AddTokenViewModel : ViewModel(), OnTransactionStateChange, FungibleTokenListUpdateListener {

    val tokenListLiveData = MutableLiveData<List<TokenItem>>()
    var cadenceExecuteLiveData = MutableLiveData<Boolean>()
    private val service by lazy { retrofitApi().create(ApiService::class.java) }

    private val coinList = mutableListOf<TokenItem>()

    private var transactionIds = mutableListOf<String>()

    private var keyword = ""
    private var isShowVerifiedToken = true

    init {
        TransactionStateManager.addOnTransactionStateChange(this)
        FungibleTokenListManager.addTokenListUpdateListener(this)
    }

    fun load() {
        viewModelIOScope(this) {
            coinList.clear()
            val tokenResponse = service.getAddTokenList(FullFungibleTokenListType.FLOW.name, chainNetWorkString())
            if (tokenResponse.tokens.isEmpty()) {
                return@viewModelIOScope
            }

            coinList.addAll(
                tokenResponse.tokens.map { TokenItem(coin = it, isAdded = FungibleTokenListManager.isTokenAdded(it.contractId()), isAdding = false) })
            postTokenList(coinList.toList())
            onTransactionStateChange()

        }
    }

    fun search(keyword: String) {
        this.keyword = keyword
        if (keyword.isBlank()) {
            postTokenList(coinList.toList())
        } else {
            postTokenList(coinList.filter {
                it.coin.tokenName().contains(keyword, true) || it.coin.tokenSymbol().contains(keyword, true)
            })
        }
    }

    fun switchVerifiedToken(isChecked: Boolean) {
        this.isShowVerifiedToken = isChecked
        postTokenList(coinList.toList())
    }

    private fun postTokenList(tokenList: List<TokenItem>) {
        if (isShowVerifiedToken) {
            tokenListLiveData.postValue(tokenList.filter { it.coin.isVerified })
        } else {
            tokenListLiveData.postValue(tokenList.toList())
        }
    }

    fun clearSearch() {
        this.keyword = ""
        search("")
    }

    fun addToken(coin: TokenInfo) {
        ioScope {
            val transactionId = cadenceEnableToken(coin)
            if (transactionId.isNullOrBlank()) {
                toast(msgRes = R.string.add_token_failed)
            } else {
                val transactionState = TransactionState(
                    transactionId = transactionId,
                    time = System.currentTimeMillis(),
                    state = FlowTransactionStatus.PENDING.num,
                    type = TransactionState.TYPE_ADD_TOKEN,
                    data = Gson().toJson(coin)
                )
                TransactionStateManager.newTransaction(transactionState)
                pushBubbleStack(transactionState)
                transactionIds.add(transactionId)
            }
            cadenceExecuteLiveData.postValue(true)
        }
    }

    override fun onTransactionStateChange() {
        viewModelIOScope(this) {
            val transactionList = TransactionStateManager.getTransactionStateList()
            transactionList.forEach { state ->
                if (state.type == TransactionState.TYPE_ADD_TOKEN) {
                    val coin = state.tokenData()

                    if (state.isSuccess() && !FungibleTokenListManager.isTokenAdded(coin.contractId())) {
                        FungibleTokenListManager.updateTokenList(contractId = coin.contractId())
                    }
                    val index = coinList.indexOfFirst { it.coin.isSameCoin(coin.contractId()) }
                    if (index >= 0) {
                        val isAdded = FungibleTokenListManager.isTokenAdded(coin.contractId())
                        coinList[index] = TokenItem(
                            coin = coinList[index].coin,
                            isAdding = !state.isFailed() && !isAdded,
                            isAdded = isAdded,
                        )
                    }
                }
            }
            search(keyword)
        }
    }

    override fun onTokenListUpdated(list: List<FungibleToken>) {
        onTransactionStateChange()
    }

    override fun onTokenDisplayUpdated(token: FungibleToken, isAdd: Boolean) {

    }

}

enum class FullFungibleTokenListType(name: String) {
    FLOW("flow"),
    EVM("evm")
}