package com.flowfoundation.wallet.manager.evm

import com.flowfoundation.wallet.manager.flowjvm.cadenceCOALink
import com.flowfoundation.wallet.manager.flowjvm.cadenceCheckCOALink
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.transaction.TransactionStateWatcher
import com.flowfoundation.wallet.manager.transaction.isExecuteFinished
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.getCOALinkCheckedAddressSet
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.setCOALinkCheckedAddresssSet
import com.flowfoundation.wallet.utils.uiScope
import com.nftco.flow.sdk.FlowTransactionStatus


object COALinkCheckManager {

    private val addressSet: MutableSet<String> by lazy {
        getCOALinkCheckedAddressSet().toMutableSet()
    }

    private fun setAddressChecked(address: String) {
        ioScope {
            if (addressSet.add(address)) {
                setCOALinkCheckedAddresssSet(addressSet)
            }
        }
    }

    fun checkCOALink(): Boolean {
        val walletAddress = WalletManager.wallet()?.walletAddress() ?: return true
        if (addressSet.contains(walletAddress)) {
            return true
        }
        val isLinked = cadenceCheckCOALink(walletAddress) ?: true
        if (isLinked) {
            setAddressChecked(walletAddress)
        }
        return isLinked
    }

    fun createCOALink(callback: (isSuccess: Boolean) -> Unit) {
        ioScope {
            try {
                val txId = cadenceCOALink()
                val transactionState = TransactionState(
                    transactionId = txId!!,
                    time = System.currentTimeMillis(),
                    state = FlowTransactionStatus.UNKNOWN.num,
                    type = TransactionState.TYPE_TRANSACTION_DEFAULT,
                    data = ""
                )
                TransactionStateManager.newTransaction(transactionState)
                uiScope { pushBubbleStack(transactionState) }
                TransactionStateWatcher(txId).watch {
                    if (it.isExecuteFinished()) {
                        callback.invoke(true)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback.invoke(false)
            }
        }
    }
}