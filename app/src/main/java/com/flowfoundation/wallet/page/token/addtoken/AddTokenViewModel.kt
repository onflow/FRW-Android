package com.flowfoundation.wallet.page.token.addtoken

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.nftco.flow.sdk.FlowTransactionStatus
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.coin.TokenStateChangeListener
import com.flowfoundation.wallet.manager.coin.TokenStateManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceEnableToken
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.page.token.addtoken.model.TokenItem
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.viewModelIOScope

class AddTokenViewModel : ViewModel(), OnTransactionStateChange, TokenStateChangeListener {

    val tokenListLiveData = MutableLiveData<List<TokenItem>>()
    var cadenceExecuteLiveData = MutableLiveData<Boolean>()

    private val coinList = mutableListOf<TokenItem>()

    private var transactionIds = mutableListOf<String>()

    private var keyword = ""

    init {
        TransactionStateManager.addOnTransactionStateChange(this)
        TokenStateManager.addListener(this)
    }

    fun load() {
        viewModelIOScope(this) {
            coinList.clear()
            coinList.addAll(
                FlowCoinListManager.coinList().map { TokenItem(coin = it, isAdded = TokenStateManager.isTokenAdded(it), isAdding = false) })
            tokenListLiveData.postValue(coinList.toList())

            onTransactionStateChange()

            TokenStateManager.fetchState()
        }
    }

    fun search(keyword: String) {
        this.keyword = keyword
        if (keyword.isBlank()) {
            tokenListLiveData.postValue(coinList.toList())
        } else {
            tokenListLiveData.postValue(coinList.filter {
                it.coin.name.lowercase().contains(keyword.lowercase()) || it.coin.symbol.lowercase().contains(keyword.lowercase())
            })
        }
    }

    fun clearSearch() {
        this.keyword = ""
        search("")
    }

    fun addToken(coin: FlowCoin) {
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

                    if (state.isSuccess() && !TokenStateManager.isTokenAdded(coin)) {
                        TokenStateManager.fetchStateSingle(state.tokenData(), cache = true)
                    }
                    val index = coinList.indexOfFirst { it.coin.isSameCoin(coin.contractId()) }
                    if (index >= 0) {
                        val isAdded = TokenStateManager.isTokenAdded(coin)
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

    override fun onTokenStateChange() {
        onTransactionStateChange()
    }
}