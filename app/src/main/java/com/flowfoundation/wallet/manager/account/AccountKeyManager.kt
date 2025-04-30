package com.flowfoundation.wallet.manager.account

import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.wallet.Wallet
import com.flowfoundation.wallet.wallet.AccountManager as FlowAccountManager
import com.flowfoundation.wallet.wallet.KeyManager
import org.onflow.flow.models.SigningAlgorithm
import org.onflow.flow.models.TransactionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AccountKeyManager {
    private val TAG = AccountKeyManager::class.java.simpleName
    private var revokingIndexId = -1

    // New Flow Wallet Kit SDK instances
    private val wallet = Wallet()
    private val accountManager = FlowAccountManager(wallet)
    private val keyManager = KeyManager()

    fun getRevokingIndexId(): Int {
        return revokingIndexId
    }

    suspend fun revokeAccountKey(indexId: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                revokingIndexId = indexId
                val accounts = accountManager.accounts.first()
                val currentAccount = accounts.values.flatten().firstOrNull()
                
                if (currentAccount == null) {
                    loge(TAG, "No current account found")
                    return@withContext false
                }

                val transaction = wallet.createTransaction {
                    script = """
                        transaction(keyIndex: Int) {
                            prepare(signer: AuthAccount) {
                                signer.removePublicKey(keyIndex)
                            }
                        }
                    """.trimIndent()
                    addArgument { int(indexId) }
                    addAuthorizer { currentAccount }
                }

                val result = wallet.sendTransaction(transaction)
                logd(TAG, "Key revocation transaction sent: ${result.id}")

                // Monitor transaction status
                var status = result.status
                while (status == TransactionStatus.PENDING) {
                    status = wallet.getTransactionStatus(result.id)
                }

                status == TransactionStatus.SEALED
            } catch (e: Exception) {
                loge(TAG, "Error revoking account key: ${e.message}")
                false
            }
        }
    }
}