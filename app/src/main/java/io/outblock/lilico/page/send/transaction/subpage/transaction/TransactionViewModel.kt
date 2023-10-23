package io.outblock.lilico.page.send.transaction.subpage.transaction

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.nftco.flow.sdk.FlowTransactionStatus
import io.outblock.lilico.manager.account.AccountManager
import io.outblock.lilico.manager.coin.CoinRateManager
import io.outblock.lilico.manager.coin.FlowCoin
import io.outblock.lilico.manager.coin.FlowCoinListManager
import io.outblock.lilico.manager.coin.OnCoinRateUpdate
import io.outblock.lilico.manager.flowjvm.cadenceTransferToken
import io.outblock.lilico.manager.transaction.TransactionState
import io.outblock.lilico.manager.transaction.TransactionStateManager
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.network.model.UserInfoData
import io.outblock.lilico.page.send.transaction.subpage.amount.model.TransactionModel
import io.outblock.lilico.page.window.bubble.tools.pushBubbleStack
import io.outblock.lilico.utils.viewModelIOScope
import io.outblock.lilico.wallet.toAddress

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