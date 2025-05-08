package com.flowfoundation.wallet.manager.backup

import com.flow.wallet.CryptoProvider
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.wallet.KeyWallet
import org.onflow.flow.models.DomainTag
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.Signer
import org.onflow.flow.models.SigningAlgorithm

/**
 * A CryptoProvider implementation that wraps a SeedPhraseKey and integrates with Flow-Wallet-Kit
 * This provider is used for backup and restore operations, ensuring proper key management
 * and cryptographic operations using the Flow-Wallet-Kit standards.
 */
class BackupCryptoProvider(
    private val seedPhraseKey: SeedPhraseKey,
    private val keyWallet: KeyWallet? = null
) : CryptoProvider {

    /**
     * Get the mnemonic phrase associated with this key
     * @return Space-separated mnemonic words
     */
    fun getMnemonic(): String {
        return seedPhraseKey.mnemonic.joinToString(" ")
    }

    /**
     * Get the key weight for this provider
     * @return Key weight value (1000 for full weight)
     */
    override fun getKeyWeight(): Int {
        return 1000 // Full weight key
    }

    /**
     * Get the public key in hex format
     * @return Hex-encoded public key string
     */
    @OptIn(ExperimentalStdlibApi::class)
    override fun getPublicKey(): String {
        return seedPhraseKey.publicKey(SigningAlgorithm.ECDSA_P256)?.toHexString() ?: ""
    }

    /**
     * Sign user authentication data
     * @param jwt JWT token to sign
     * @return Hex-encoded signature
     */
    override suspend fun getUserSignature(jwt: String): String {
        return signData(DomainTag.User.bytes + jwt.encodeToByteArray())
    }

    /**
     * Sign arbitrary data
     * @param data Data to sign
     * @return Hex-encoded signature
     */
    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun signData(data: ByteArray): String {
        return seedPhraseKey.sign(data, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA3_256).toHexString()
    }

    /**
     * Get a Signer implementation for transaction signing
     * @return Signer implementation
     */
    override fun getSigner(): Signer {
        return object : Signer {
            override var address: String = ""
            override var keyIndex: Int = 0
            
            override suspend fun sign(transaction: org.onflow.flow.models.Transaction?, bytes: ByteArray): ByteArray {
                return seedPhraseKey.sign(bytes, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA3_256)
            }

            override suspend fun sign(bytes: ByteArray): ByteArray {
                return seedPhraseKey.sign(bytes, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA3_256)
            }
        }
    }

    /**
     * Get the hashing algorithm used by this provider
     * @return SHA3_256 hashing algorithm
     */
    override fun getHashAlgorithm(): HashingAlgorithm {
        return HashingAlgorithm.SHA3_256
    }

    /**
     * Get the signing algorithm used by this provider
     * @return ECDSA_P256 signing algorithm
     */
    override fun getSignatureAlgorithm(): SigningAlgorithm {
        return SigningAlgorithm.ECDSA_P256
    }

    /**
     * Get the associated KeyWallet if available
     * @return KeyWallet instance or null
     */
    fun getKeyWallet(): KeyWallet? {
        return keyWallet
    }
}