package com.flowfoundation.wallet.page.send.transaction.subpage.transaction

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.coin.CoinRateManager
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.coin.OnCoinRateUpdate
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.evm.sendEthereumTransaction
import com.flowfoundation.wallet.manager.flowjvm.cadenceFundFlowToCOAAccount
import com.flowfoundation.wallet.manager.flowjvm.cadenceSendEVMTransaction
import com.flowfoundation.wallet.manager.flowjvm.cadenceTransferFlowToEvmAddress
import com.flowfoundation.wallet.manager.flowjvm.cadenceTransferToken
import com.flowfoundation.wallet.manager.flowjvm.cadenceWithdrawTokenFromCOAAccount
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.page.send.transaction.subpage.amount.model.TransactionModel
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.viewModelIOScope
import com.flowfoundation.wallet.wallet.removeAddressPrefix
import com.flowfoundation.wallet.wallet.toAddress
import com.flowfoundation.wallet.widgets.webview.evm.model.EvmTransaction
import com.google.gson.Gson
import com.nftco.flow.sdk.FlowTransactionStatus

class TransactionViewModel : ViewModel(), OnCoinRateUpdate {

    val userInfoLiveData = MutableLiveData<UserInfoData>()

    val amountConvertLiveData = MutableLiveData<Float>()

    val resultLiveData = MutableLiveData<Boolean>()

    private val addressPattern by lazy { Regex("^0x[a-fA-F0-9]{16}\$") }

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
            if (coin.isFlowCoin()) {
                val fromAddress = transaction.fromAddress
                val toAddress = transaction.target.address.orEmpty().toAddress()
                if (EVMWalletManager.isEVMWalletAddress(fromAddress)) {
                    if (isFlowAddress(toAddress)) {
                        // COA -> Flow
                        withdrawFromCOAAccount(transaction.amount, toAddress)
                    } else {
                        // COA -> EOA/COA
                        evmTransaction(toAddress, transaction.amount)
                    }
                } else {
                    if (isFlowAddress(toAddress)) {
                        // Flow -> Flow
                        transferToken(coin)
                    } else if(EVMWalletManager.isEVMWalletAddress(toAddress)){
                        // Flow -> Self COA
                        fundToCOAAccount(transaction.amount)
                    } else {
                        // Flow -> EOA/COA
                        transferFlowToEVM(toAddress, transaction.amount)
                    }
                }
            } else {
                transferToken(coin)
            }
        }
    }

    private fun isFlowAddress(address: String): Boolean {
        return addressPattern.matches(address)
    }

    private suspend fun transferToken(coin: FlowCoin) {
        val txId = cadenceTransferToken(coin, transaction.target.address.orEmpty().toAddress(), transaction.amount.toDouble())
        postTransaction(txId)
    }

    private suspend fun evmTransaction(to: String, amount: Float) {
        val txId = cadenceSendEVMTransaction(to.removeAddressPrefix(), amount.toBigDecimal(), byteArrayOf(), 100000)
        postTransaction(txId)
    }

    private suspend fun withdrawFromCOAAccount(amount: Float, toAddress: String) {
        val txId = cadenceWithdrawTokenFromCOAAccount(amount, toAddress)
        postTransaction(txId)
    }

    private suspend fun fundToCOAAccount(amount: Float) {
        val txId = cadenceFundFlowToCOAAccount(amount)
        postTransaction(txId)
    }

    private suspend fun transferFlowToEVM(to: String, amount: Float) {
        val txId = cadenceTransferFlowToEvmAddress(to, amount)
        postTransaction(txId)
    }

    private fun postTransaction(txId: String?) {
        resultLiveData.postValue(txId != null)
        if (txId.isNullOrBlank()) {
            return
        }
        val transactionState = TransactionState(
            transactionId = txId,
            time = System.currentTimeMillis(),
            state = FlowTransactionStatus.PENDING.num,
            type = TransactionState.TYPE_TRANSFER_COIN,
            data = Gson().toJson(transaction),
        )
        TransactionStateManager.newTransaction(transactionState)
        pushBubbleStack(transactionState)
    }

    override fun onCoinRateUpdate(coin: FlowCoin, price: Float) {
        if (coin.symbol != transaction.coinSymbol) {
            return
        }
        amountConvertLiveData.postValue(price * transaction.amount)
    }
}