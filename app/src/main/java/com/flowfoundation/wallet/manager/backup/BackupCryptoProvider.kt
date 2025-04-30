package com.flowfoundation.wallet.manager.backup

import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.wallet.KeyManager
import org.onflow.flow.models.SigningAlgorithm
import org.onflow.flow.models.HashingAlgorithm
import com.flow.wallet.CryptoProvider
import org.onflow.flow.models.bytesToHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupCryptoProvider(private val mnemonic: String) : CryptoProvider {
    private val TAG = BackupCryptoProvider::class.java.simpleName
    private val keyManager = KeyManager()
    private val key = keyManager.createSeedPhraseKey(mnemonic)

    override fun getPublicKey(): String {
        return keyManager.getPublicKey(key, SigningAlgorithm.ECDSA_P256) ?: ""
    }

    override suspend fun getUserSignature(message: String): String {
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

    override fun getHashAlgorithm(): HashingAlgorithm {
        return HashingAlgorithm.SHA3_256
    }

    override fun getSignatureAlgorithm(): SigningAlgorithm {
        return SigningAlgorithm.ECDSA_P256
    }

    fun getMnemonic(): String {
        return mnemonic
    }

    override fun getKeyWeight(): Int {
        return 500
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

    override fun getSigner(): org.onflow.flow.models.Signer {
        return object : org.onflow.flow.models.Signer {
            override fun sign(data: ByteArray): ByteArray {
                return try {
                    keyManager.sign(key, data, SigningAlgorithm.ECDSA_P256)
                } catch (e: Exception) {
                    loge(TAG, "Error in signer: ${e.message}")
                    ByteArray(0)
                }
            }
        }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}