package io.outblock.lilico.page.token.addtoken

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.nftco.flow.sdk.FlowTransactionStatus
import io.outblock.lilico.R
import io.outblock.lilico.manager.coin.FlowCoin
import io.outblock.lilico.manager.coin.FlowCoinListManager
import io.outblock.lilico.manager.coin.TokenStateChangeListener
import io.outblock.lilico.manager.coin.TokenStateManager
import io.outblock.lilico.manager.flowjvm.cadenceEnableToken
import io.outblock.lilico.manager.transaction.OnTransactionStateChange
import io.outblock.lilico.manager.transaction.TransactionState
import io.outblock.lilico.manager.transaction.TransactionStateManager
import io.outblock.lilico.page.token.addtoken.model.TokenItem
import io.outblock.lilico.page.window.bubble.tools.pushBubbleStack
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.toast
import io.outblock.lilico.utils.viewModelIOScope

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
                FlowCoinListManager.coinList().map { TokenItem(coin = it, isAdded = TokenStateManager.isTokenAdded(it.address()), isAdding = false) })
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

                    if (state.isSuccess() && !TokenStateManager.isTokenAdded(coin.address())) {
                        TokenStateManager.fetchStateSingle(state.tokenData(), cache = true)
                    }
                    val index = coinList.indexOfFirst { it.coin.symbol == coin.symbol }
                    if (index >= 0) {
                        val isAdded = TokenStateManager.isTokenAdded(coin.address())
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