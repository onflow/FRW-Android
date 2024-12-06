package com.flowfoundation.wallet.page.dialog.processing.send

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.coin.CoinRateManager
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.coin.OnCoinRateUpdate
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.utils.viewModelIOScope
import java.math.BigDecimal

class SendProcessingViewModel : ViewModel(), OnTransactionStateChange, OnCoinRateUpdate {
    val userInfoLiveData = MutableLiveData<UserInfoData>()

    val amountConvertLiveData = MutableLiveData<BigDecimal>()

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
                val coin = FlowCoinListManager.getCoinById(coinData.coinId) ?: return@viewModelIOScope
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

    override fun onCoinRateUpdate(coin: FlowCoin, price: BigDecimal) {
        amountConvertLiveData.postValue(price * state.coinData().amount)
    }
}