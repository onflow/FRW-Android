package com.flowfoundation.wallet.manager.key

import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.model.LocalSwitchAccount
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.wallet.Wallet
import com.flowfoundation.wallet.wallet.AccountManager as FlowAccountManager
import com.flowfoundation.wallet.wallet.KeyManager
import org.onflow.flow.models.SigningAlgorithm
import org.onflow.flow.models.HashingAlgorithm
import com.flow.wallet.CryptoProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.onflow.flow.models.bytesToHex

object CryptoProviderManager {
    private val TAG = CryptoProviderManager::class.java.simpleName
    
    // New Flow Wallet Kit SDK instances
    private val wallet = Wallet()
    private val accountManager = FlowAccountManager(wallet)
    private val keyManager = KeyManager()

    suspend fun getCurrentCryptoProvider(): CryptoProvider? {
        return withContext(Dispatchers.IO) {
            try {
                val accounts = accountManager.accounts.first()
                val currentAccount = accounts.values.flatten().firstOrNull() ?: return@withContext null
                NewCryptoProvider(currentAccount.key)
            } catch (e: Exception) {
                loge(TAG, "Error getting current crypto provider: ${e.message}")
                null
            }
        }
    }

    suspend fun getSwitchAccountCryptoProvider(account: Account): CryptoProvider? {
        return withContext(Dispatchers.IO) {
            try {
                val accounts = accountManager.accounts.first()
                accounts.values.flatten()
                    .find { it.address == account.wallet?.mainnetWallet()?.address() }
                    ?.let { flowAccount ->
                        NewCryptoProvider(flowAccount.key)
                    }
            } catch (e: Exception) {
                loge(TAG, "Error getting switch account crypto provider: ${e.message}")
                null
            }
        }
    }

    suspend fun getSwitchAccountCryptoProvider(switchAccount: LocalSwitchAccount): CryptoProvider? {
        return withContext(Dispatchers.IO) {
            try {
                val accounts = accountManager.accounts.first()
                val targetAccount = accounts.values.flatten().find { it.id == switchAccount.userId } ?: return@withContext null
                NewCryptoProvider(targetAccount.key)
            } catch (e: Exception) {
                loge(TAG, "Error getting switch account crypto provider: ${e.message}")
                null
            }
        }
    }

    suspend fun generateAccountCryptoProvider(mnemonic: String): CryptoProvider? {
        return withContext(Dispatchers.IO) {
            try {
                val key = keyManager.createSeedPhraseKey(mnemonic)
                NewCryptoProvider(key)
            } catch (e: Exception) {
                loge(TAG, "Error generating account crypto provider: ${e.message}")
                null
            }
        }
    }
}

interface CryptoProvider {
    fun getPublicKey(): String
    suspend fun signData(message: String): String
    suspend fun signData(data: ByteArray): String
}

class NewCryptoProvider(private val key: com.flowfoundation.wallet.wallet.Key) : CryptoProvider {
    private val TAG = NewCryptoProvider::class.java.simpleName
    private val keyManager = KeyManager()

    override fun getPublicKey(): String {
        return keyManager.getPublicKey(key, SigningAlgorithm.ECDSA_P256) ?: ""
    }

    override suspend fun signData(message: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val signature = keyManager.sign(key, message.toByteArray(), SigningAlgorithm.ECDSA_P256)
                signature?.toHexString() ?: ""
            } catch (e: Exception) {
                loge(TAG, "Error signing message: ${e.message}")
                ""
            }
        }
    }

    override suspend fun signData(data: ByteArray): String {
        return withContext(Dispatchers.IO) {
            try {
                val signature = keyManager.sign(key, data, SigningAlgorithm.ECDSA_P256)
                signature?.toHexString() ?: ""
            } catch (e: Exception) {
                loge(TAG, "Error signing data: ${e.message}")
                ""
            }
        }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}