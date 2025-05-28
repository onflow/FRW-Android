package com.flowfoundation.wallet.manager.backup

import com.flow.wallet.CryptoProvider
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.wallet.KeyWallet
import com.flowfoundation.wallet.utils.logd
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
    private val keyWallet: KeyWallet? = null,
    private val signingAlgorithm: SigningAlgorithm = SigningAlgorithm.ECDSA_secp256k1,
    private val hashingAlgorithm: HashingAlgorithm? = null // Dynamic hashing algorithm from on-chain key
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
        return seedPhraseKey.publicKey(signingAlgorithm)?.toHexString() ?: ""
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
        // Use the dynamically determined hashing algorithm if available,
        // otherwise fall back to the provider's default
        val effectiveHashingAlgorithm = hashingAlgorithm ?: getHashAlgorithm()
        return seedPhraseKey.sign(data, signingAlgorithm, effectiveHashingAlgorithm).toHexString()
    }

    /**
     * Get a Signer implementation for transaction signing
     * @return Signer implementation
     */
    override fun getSigner(hashingAlgorithm: HashingAlgorithm): org.onflow.flow.models.Signer {
        logd("BackupCryptoProvider", "Creating KMM Signer with signingAlgorithm: $signingAlgorithm, hashingAlgorithm: $hashingAlgorithm")
        return object : org.onflow.flow.models.Signer {
            override var address: String = ""
            override var keyIndex: Int = 0
            
            override suspend fun sign(transaction: org.onflow.flow.models.Transaction?, bytes: ByteArray): ByteArray {
                logd("BackupCryptoProvider", "KMM Signer.sign(transaction, bytes) called - FIXED NO DOUBLE HASH")
                logd("BackupCryptoProvider", "  Address: $address")
                logd("BackupCryptoProvider", "  KeyIndex: $keyIndex")
                logd("BackupCryptoProvider", "  Transaction: ${transaction?.id ?: "null"}")
                logd("BackupCryptoProvider", "  Bytes to sign (${bytes.size} bytes): ${bytes.take(50).joinToString("") { "%02x".format(it) }}...")
                logd("BackupCryptoProvider", "  Using signing algorithm: $signingAlgorithm")
                logd("BackupCryptoProvider", "  Using hashing algorithm: $hashingAlgorithm")
                
                // CRITICAL FIX: bytes are already hashed by KMM SDK, sign them directly
                val signature = signRawBytes(bytes, hashingAlgorithm)
                logd("BackupCryptoProvider", "  Generated signature (${signature.size} bytes): ${signature.take(32).joinToString("") { "%02x".format(it) }}...")
                return signature
            }

            override suspend fun sign(bytes: ByteArray): ByteArray {
                logd("BackupCryptoProvider", "KMM Signer.sign(bytes) called - FIXED NO DOUBLE HASH")
                logd("BackupCryptoProvider", "  Address: $address")
                logd("BackupCryptoProvider", "  KeyIndex: $keyIndex")
                logd("BackupCryptoProvider", "  Bytes to sign (${bytes.size} bytes): ${bytes.take(50).joinToString("") { "%02x".format(it) }}...")
                logd("BackupCryptoProvider", "  Using signing algorithm: $signingAlgorithm")
                logd("BackupCryptoProvider", "  Using hashing algorithm: $hashingAlgorithm")
                
                // CRITICAL FIX: bytes are already hashed by KMM SDK, sign them directly
                val signature = signRawBytes(bytes, hashingAlgorithm)
                logd("BackupCryptoProvider", "  Generated signature (${signature.size} bytes): ${signature.take(32).joinToString("") { "%02x".format(it) }}...")
                return signature
            }
            
            override suspend fun signWithDomain(bytes: ByteArray, domain: ByteArray): ByteArray {
                logd("BackupCryptoProvider", "KMM Signer.signWithDomain() called")
                logd("BackupCryptoProvider", "  Domain: ${domain.take(32).joinToString("") { "%02x".format(it) }}...")
                logd("BackupCryptoProvider", "  Bytes: ${bytes.take(32).joinToString("") { "%02x".format(it) }}...")
                // For domain signing, we need to hash the domain + bytes combination
                return seedPhraseKey.sign(domain + bytes, signingAlgorithm, hashingAlgorithm)
            }
            
            override suspend fun signAsUser(bytes: ByteArray): ByteArray {
                logd("BackupCryptoProvider", "KMM Signer.signAsUser() called")
                return signWithDomain(bytes, org.onflow.flow.models.DomainTag.User.bytes)
            }
            
            override suspend fun signAsTransaction(bytes: ByteArray): ByteArray {
                logd("BackupCryptoProvider", "KMM Signer.signAsTransaction() called")
                return signWithDomain(bytes, org.onflow.flow.models.DomainTag.Transaction.bytes)
            }
        }
    }

    /**
     * Sign raw bytes without additional hashing (for KMM SDK transaction signing)
     * The bytes are already hashed by the KMM SDK
     */
    private suspend fun signRawBytes(hashedData: ByteArray, hashingAlgorithm: HashingAlgorithm): ByteArray {
        logd("BackupCryptoProvider", "[BACKUP] signRawBytes() - input is already hashed (${hashedData.size} bytes): ${hashedData.take(32).joinToString("") { "%02x".format(it) }}...")
        logd("BackupCryptoProvider", "[BACKUP] Using signAlgo: $signingAlgorithm with SeedPhraseKey.signHash()")
        
        // Use the new signHash method from SeedPhraseKey to sign already-hashed data
        val signature = seedPhraseKey.signHash(hashedData, signingAlgorithm)
        
        logd("BackupCryptoProvider", "[BACKUP] signRawBytes() result (${signature.size} bytes): ${signature.joinToString("") { "%02x".format(it) }}")
        return signature
    }

    /**
     * Get the hashing algorithm used by this provider
     * @return The configured hashing algorithm or a sensible default
     */
    override fun getHashAlgorithm(): HashingAlgorithm {
        // Return the dynamically determined hashing algorithm if available
        return hashingAlgorithm ?: run {
            // Fallback to sensible defaults based on signing algorithm
            when (signingAlgorithm) {
                SigningAlgorithm.ECDSA_secp256k1 -> HashingAlgorithm.SHA2_256
                SigningAlgorithm.ECDSA_P256 -> HashingAlgorithm.SHA3_256
                else -> HashingAlgorithm.SHA3_256
            }
        }
    }

    /**
     * Get the signing algorithm used by this provider
     * @return The configured signing algorithm
     */
    override fun getSignatureAlgorithm(): SigningAlgorithm {
        return signingAlgorithm
    }

    /**
     * Get the associated KeyWallet if available
     * @return KeyWallet instance or null
     */
    fun getKeyWallet(): KeyWallet? {
        return keyWallet
    }
}