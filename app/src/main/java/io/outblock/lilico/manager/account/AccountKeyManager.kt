package io.outblock.lilico.manager.account

import com.nftco.flow.sdk.FlowTransactionStatus
import io.outblock.lilico.manager.flowjvm.CADENCE_REVOKE_ACCOUNT_KEY
import io.outblock.lilico.manager.flowjvm.transactionByMainWallet
import io.outblock.lilico.manager.transaction.TransactionState
import io.outblock.lilico.manager.transaction.TransactionStateManager
import io.outblock.lilico.page.window.bubble.tools.pushBubbleStack


object AccountKeyManager {
    private var revokingIndexId = -1

    fun getRevokingIndexId(): Int {
        return revokingIndexId
    }

    suspend fun revokeAccountKey(indexId: Int): Boolean {
        try {
            revokingIndexId = indexId
            val txId = CADENCE_REVOKE_ACCOUNT_KEY.transactionByMainWallet {
                arg { uint32(indexId) }
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