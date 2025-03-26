package com.flowfoundation.wallet.manager.account

import org.onflow.flow.sdk.FlowTransactionStatus
import com.flowfoundation.wallet.manager.flowjvm.CadenceScript
import com.flowfoundation.wallet.manager.flowjvm.transactionByMainWallet
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack


object AccountKeyManager {
    private var revokingIndexId = -1

    fun getRevokingIndexId(): Int {
        return revokingIndexId
    }

    suspend fun revokeAccountKey(indexId: Int): Boolean {
        try {
            revokingIndexId = indexId
            val txId = CadenceScript.CADENCE_REVOKE_ACCOUNT_KEY.transactionByMainWallet {
                arg { int(indexId) }
            }
            val transactionState = TransactionState(
                transactionId = txId!!,
                time = System.currentTimeMillis(),
                state = FlowTransactionStatus.PENDING.num,
                type = TransactionState.TYPE_REVOKE_KEY,
                data = ""
            )
            TransactionStateManager.newTransaction(transactionState)
            pushBubbleStack(transactionState)
            return true
        } catch (e: Exception) {
            return false
        }
    }
}