package com.flowfoundation.wallet.page.swap

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.nftco.flow.sdk.FlowTransactionStatus
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.manager.token.FungibleTokenUpdateListener
import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.SwapEstimateResponse
import com.flowfoundation.wallet.network.retrofitWithHost
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.safeRun
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.viewModelIOScope
import java.math.BigDecimal

class SwapViewModel : ViewModel(), FungibleTokenUpdateListener {

    val fromCoinLiveData = MutableLiveData<FungibleToken>()
    val toCoinLiveData = MutableLiveData<FungibleToken>()
    val onBalanceUpdate = MutableLiveData<Boolean>()
    val onCoinRateUpdate = MutableLiveData<Boolean>()
    val onEstimateFromUpdate = MutableLiveData<BigDecimal>()
    val onEstimateToUpdate = MutableLiveData<BigDecimal>()

    val onEstimateLoading = MutableLiveData<Boolean>()
    val estimateLiveData = MutableLiveData<SwapEstimateResponse.Data>()

    val swapTransactionStateLiveData = MutableLiveData<Boolean>()

    private val balanceMap: MutableMap<String, BigDecimal> = mutableMapOf()
    private val coinRateMap: MutableMap<String, BigDecimal> = mutableMapOf()

    var exactToken = ExactToken.FROM
        private set

    init {
        FungibleTokenListManager.addTokenUpdateListener(this)
    }

    fun initFromCoin(contractId: String) {
        FungibleTokenListManager.getTokenById(contractId)?.let { token ->
            fromCoinLiveData.value = token
            ioScope {
                FungibleTokenListManager.updateTokenInfo(contractId)
            }
        }
    }

    fun fromCoinBalance(): BigDecimal = if (fromCoin() == null) BigDecimal.ZERO else balanceMap[fromCoin()?.contractId()] ?: BigDecimal.ZERO

    fun fromCoin() = fromCoinLiveData.value
    fun toCoin() = toCoinLiveData.value

    fun fromCoinRate(): BigDecimal = coinRateMap[fromCoin()?.contractId()] ?: BigDecimal.ZERO

    fun updateFromCoin(coin: FungibleToken) {
        if (fromCoin() == coin) return
        fromCoinLiveData.value = coin
        requestEstimate()
        ioScope {
            FungibleTokenListManager.updateTokenInfo(coin.contractId())
        }
    }

    fun updateToCoin(coin: FungibleToken) {
        if (toCoin() == coin) return
        toCoinLiveData.value = coin
        requestEstimate()
        ioScope {
            FungibleTokenListManager.updateTokenInfo(coin.contractId())
        }
    }

    fun updateExactToken(exactToken: ExactToken) {
        this.exactToken = exactToken
        requestEstimate()
    }

    fun switchCoin() {
        val fromCoin = fromCoin()
        val toCoin = toCoin()
        if (fromCoin == null || toCoin == null) {
            return
        }

        exactToken = if (exactToken == ExactToken.FROM) ExactToken.TO else ExactToken.FROM

        updateFromCoin(toCoin)
        updateToCoin(fromCoin)
    }

    fun swap() {
        val data = estimateLiveData.value ?: return
        ioScope {
            val txId = swapSend(data)
            safeRun { swapTransactionStateLiveData.postValue(!txId.isNullOrBlank()) }
            if (txId.isNullOrBlank()) {
                toast(msgRes = R.string.swap_failed)
                return@ioScope
            }
            val transactionState = TransactionState(
                transactionId = txId,
                time = System.currentTimeMillis(),
                state = FlowTransactionStatus.PENDING.num,
                type = TransactionState.TYPE_TRANSACTION_DEFAULT,
                data = Gson().toJson(data),
            )
            TransactionStateManager.newTransaction(transactionState)
            pushBubbleStack(transactionState)
        }
    }

    override fun onTokenUpdated(token: FungibleToken) {
        balanceMap[token.contractId()] = token.tokenBalance()
        onBalanceUpdate.value = true
        coinRateMap[token.contractId()] = token.tokenPrice()
        onCoinRateUpdate.value = true
    }

    private fun requestEstimate() {
        if (fromCoin() == null || toCoin() == null) return
        val binding = swapPageBinding() ?: return
        if (binding.fromAmount() == BigDecimal.ZERO && binding.toAmount() == BigDecimal.ZERO) return

        onEstimateLoading.value = true
        viewModelIOScope(this) {
            val response = kotlin.runCatching {
                retrofitWithHost("https://lilico.app").create(ApiService::class.java).getSwapEstimate(
                    network = chainNetWorkString(),
                    inToken = fromCoin()!!.contractId(),
                    outToken = toCoin()!!.contractId(),
                    inAmount = if (exactToken == ExactToken.FROM) binding.fromAmount().toFloat() else null,
                    outAmount = if (exactToken == ExactToken.TO) binding.toAmount().toFloat() else null,
                )
            }.getOrNull()

            val data = response?.data

            if (data == null) {
                onEstimateLoading.postValue(false)
                return@viewModelIOScope
            }

            val matched = if (exactToken == ExactToken.FROM) data.tokenInAmount == binding.fromAmount() else data.tokenOutAmount == binding.toAmount()
            if (matched) {
                if (exactToken == ExactToken.FROM) onEstimateToUpdate.postValue(data.tokenOutAmount) else onEstimateFromUpdate.postValue(data.tokenInAmount)
                onEstimateLoading.postValue(false)
                data.let {
                    estimateLiveData.postValue(it)
                }
            }
        }
    }

}

enum class ExactToken {
    FROM,
    TO
}