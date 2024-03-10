package com.flowfoundation.wallet.page.send.transaction.subpage.transaction

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.nftco.flow.sdk.FlowTransactionStatus
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.coin.CoinRateManager
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.coin.OnCoinRateUpdate
import com.flowfoundation.wallet.manager.flowjvm.cadenceTransferToken
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.page.send.transaction.subpage.amount.model.TransactionModel
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.viewModelIOScope
import com.flowfoundation.wallet.wallet.toAddress

class TransactionViewModel : ViewModel(), OnCoinRateUpdate {

    val userInfoLiveData = MutableLiveData<UserInfoData>()

    val amountConvertLiveData = MutableLiveData<Float>()

    val resultLiveData = MutableLiveData<Boolean>()

    lateinit var transaction: TransactionModel

    init {
        CoinRateManager.addListener(this)
    }

    fun bindTransaction(transaction: TransactionModel) {
        this.transaction = transaction
    }

    fun load() {
        viewModelIOScope(this) {
            AccountManager.userInfo()?.let { userInfo ->
                WalletManager.selectedWalletAddress().let {
                    userInfoLiveData.postValue(userInfo.apply { address = it })
                }
            }

            val flow = FlowCoinListManager.coinList().first { it.symbol == transaction.coinSymbol }
            CoinRateManager.fetchCoinRate(flow)
        }
    }

    fun send(coin: FlowCoin) {
        viewModelIOScope(this) {
            val tid = cadenceTransferToken(coin, transaction.target.address.orEmpty().toAddress(), transaction.amount.toDouble())
            resultLiveData.postValue(tid != null)
            if (tid.isNullOrBlank()) {
                return@viewModelIOScope
            }
            val transactionState = TransactionState(
                transactionId = tid,
                time = System.currentTimeMillis(),
                state = FlowTransactionStatus.PENDING.num,
                type = TransactionState.TYPE_TRANSFER_COIN,
                data = Gson().toJson(transaction),
            )
            TransactionStateManager.newTransaction(transactionState)
            pushBubbleStack(transactionState)
        }
    }

    override fun onCoinRateUpdate(coin: FlowCoin, price: Float) {
        if (coin.symbol != transaction.coinSymbol) {
            return
        }
        amountConvertLiveData.postValue(price * transaction.amount)
    }
}