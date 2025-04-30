package com.flowfoundation.wallet.wallet

import com.flow.wallet.keys.KeyProtocol
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.storage.FileSystemStorage
import com.flow.wallet.storage.StorageProtocol
import com.flowfoundation.wallet.utils.DATA_PATH
import com.flowfoundation.wallet.utils.logd
import org.onflow.flow.models.SigningAlgorithm
import java.io.File
import java.security.SecureRandom

/**
 * Manages cryptographic keys using the Flow Wallet Kit SDK
 */
class KeyManager {
    private val storage: StorageProtocol = FileSystemStorage(
        File(DATA_PATH, "key_storage")
    )
    
    /**
     * Creates a new seed phrase key
     * @param mnemonic Optional mnemonic phrase (if null, a new one will be generated)
     * @return The created key
     */
    fun createSeedPhraseKey(mnemonic: String? = null): KeyProtocol {
        logd(TAG, "Creating seed phrase key")
        return if (mnemonic != null) {
            SeedPhraseKey(mnemonic, storage)
        } else {
            // Generate a new mnemonic
            val entropy = ByteArray(32)
            SecureRandom().nextBytes(entropy)
            SeedPhraseKey(entropy, storage)
        }
    }
    
    /**
     * Gets the public key for a specific signing algorithm
     * @param key The key to get the public key from
     * @param algorithm The signing algorithm to use
     * @return The public key as a hex string
     */
    fun getPublicKey(key: KeyProtocol, algorithm: SigningAlgorithm): String? {
        return key.publicKey(algorithm)?.toString(Charsets.ISO_8859_1)
    }
    
    /**
     * Signs data with the specified key and algorithm
     * @param key The key to use for signing
     * @param data The data to sign
     * @param algorithm The signing algorithm to use
     * @return The signed data
     */
    suspend fun sign(key: KeyProtocol, data: ByteArray, algorithm: SigningAlgorithm): ByteArray {
        return key.sign(data, algorithm, org.onflow.flow.models.HashingAlgorithm.SHA3_256)
    }
    
    companion object {
        private const val TAG = "KeyManager"
    }
} 