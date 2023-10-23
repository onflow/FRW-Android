package io.outblock.lilico.page.dialog.processing.send

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.outblock.lilico.manager.account.AccountManager
import io.outblock.lilico.manager.coin.CoinRateManager
import io.outblock.lilico.manager.coin.FlowCoin
import io.outblock.lilico.manager.coin.FlowCoinListManager
import io.outblock.lilico.manager.coin.OnCoinRateUpdate
import io.outblock.lilico.manager.transaction.OnTransactionStateChange
import io.outblock.lilico.manager.transaction.TransactionState
import io.outblock.lilico.manager.transaction.TransactionStateManager
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.network.model.UserInfoData
import io.outblock.lilico.utils.viewModelIOScope

class SendProcessingViewModel : ViewModel(), OnTransactionStateChange, OnCoinRateUpdate {
    val userInfoLiveData = MutableLiveData<UserInfoData>()

    val amountConvertLiveData = MutableLiveData<Float>()

    val stateChangeLiveData = MutableLiveData<TransactionState>()

    lateinit var state: TransactionState

    init {
        CoinRateManager.addListener(this)
    }

    fun bindTransactionState(state: TransactionState) {
        this.state = state
        TransactionStateManager.addOnTransactionStateChange(this)
    }

    fun load() {
        viewModelIOScope(this) {
            AccountManager.userInfo()?.let { userInfo ->
                WalletManager.selectedWalletAddress().let {
                    userInfoLiveData.postValue(userInfo.apply { address = it })
                }
            }
            if (state.type == TransactionState.TYPE_TRANSFER_COIN) {
                val coinData = state.coinData()
                val coin = FlowCoinListManager.getCoin(coinData.coinSymbol) ?: return@viewModelIOScope
                CoinRateManager.fetchCoinRate(coin)
            }
        }
    }

    override fun onTransactionStateChange() {
        val state = TransactionStateManager.getLastVisibleTransaction() ?: return
        if (state.transactionId != this.state.transactionId) {
            return
        }
        stateChangeLiveData.postValue(state)
    }

    override fun onCoinRateUpdate(coin: FlowCoin, price: Float) {
        amountConvertLiveData.postValue(price * state.coinData().amount)
    }
}