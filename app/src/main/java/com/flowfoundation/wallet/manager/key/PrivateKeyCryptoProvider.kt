package com.flowfoundation.wallet.manager.key

import com.flow.wallet.CryptoProvider
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.wallet.KeyWallet
import org.onflow.flow.models.DomainTag
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.Signer
import org.onflow.flow.models.SigningAlgorithm
import com.flowfoundation.wallet.utils.logd

/**
 * A CryptoProvider implementation that wraps a PrivateKey and integrates with Flow-Wallet-Kit
 * This provider is used for prefix-based accounts that use raw private keys
 */
class PrivateKeyCryptoProvider(
    private val privateKey: PrivateKey,
    private val keyWallet: KeyWallet? = null,
    private val signingAlgorithm: SigningAlgorithm = SigningAlgorithm.ECDSA_P256,
    private val hashingAlgorithm: HashingAlgorithm? = null // Dynamic hashing algorithm from on-chain key
) : CryptoProvider {
    
    private val TAG = "PrivateKeyCryptoProvider"

    init {
        logd(TAG, "Init private key provider with signAlgo=$signingAlgorithm, hashAlgo=${hashingAlgorithm ?: "default"}")
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun getPublicKey(): String {
        val publicKeyBytes = privateKey.publicKey(signingAlgorithm)
        return if (publicKeyBytes != null) {
            "0x${publicKeyBytes.toHexString()}"
        } else {
            ""
        }
    }

    override suspend fun getUserSignature(jwt: String): String {
        return signData(DomainTag.User.bytes + jwt.encodeToByteArray())
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun signData(data: ByteArray): String {
        val effectiveHashingAlgorithm = hashingAlgorithm ?: getHashAlgorithm()
        logd(TAG, "signData called. dataSize=${data.size} bytes, signAlgo=$signingAlgorithm, hashAlgo=$effectiveHashingAlgorithm")
        val signatureBytes = privateKey.sign(data, signingAlgorithm, effectiveHashingAlgorithm)
        
        // Recovery ID trimming - restore this logic since Flow-Wallet-Kit isn't handling it properly
        // Remove recovery ID if present (Flow expects 64-byte signatures, not 65-byte with recovery ID)
        val finalSignature = if (signatureBytes.size == 65) {
            logd(TAG, "Trimming recovery ID from 65-byte signature for $signingAlgorithm")
            signatureBytes.copyOfRange(0, 64) // Remove the last byte (recovery ID)
        } else {
            logd(TAG, "Using signature as-is (${signatureBytes.size} bytes)")
            signatureBytes
        }
        
        logd(TAG, "Signature generated. size=${finalSignature.size} bytes")
        return finalSignature.toHexString()
    }

    /**
     * Get a Signer implementation for transaction signing
     * @param hashingAlgorithm Hashing algorithm from on-chain key
     * @return Signer implementation
     */
    override fun getSigner(hashingAlgorithm: HashingAlgorithm): org.onflow.flow.models.Signer {
        logd("PrivateKeyCryptoProvider", "Creating KMM Signer with signingAlgorithm: $signingAlgorithm, hashingAlgorithm: $hashingAlgorithm")
        return object : org.onflow.flow.models.Signer {
            override var address: String = ""
            override var keyIndex: Int = 0
            
            override suspend fun sign(transaction: org.onflow.flow.models.Transaction?, bytes: ByteArray): ByteArray {
                logd("PrivateKeyCryptoProvider", "KMM Signer.sign(transaction, bytes) called")
                logd("PrivateKeyCryptoProvider", "  Address: $address")
                logd("PrivateKeyCryptoProvider", "  KeyIndex: $keyIndex")
                logd("PrivateKeyCryptoProvider", "  Transaction: ${transaction?.id ?: "null"}")
                logd("PrivateKeyCryptoProvider", "  Bytes to sign (${bytes.size} bytes): ${bytes.take(50).joinToString("") { "%02x".format(it) }}...")
                logd("PrivateKeyCryptoProvider", "  Using signing algorithm: $signingAlgorithm")
                logd("PrivateKeyCryptoProvider", "  Using hashing algorithm: $hashingAlgorithm")
                
                val signatureBytes = privateKey.sign(bytes, signingAlgorithm, hashingAlgorithm)
                
                // Recovery ID trimming - restore this logic since Flow-Wallet-Kit isn't handling it properly
                // Remove recovery ID if present (Flow expects 64-byte signatures, not 65-byte with recovery ID)
                val finalSignature = if (signatureBytes.size == 65) {
                    logd("PrivateKeyCryptoProvider", "  Trimming recovery ID from 65-byte signature for $signingAlgorithm")
                    signatureBytes.copyOfRange(0, 64) // Remove the last byte (recovery ID)
                } else {
                    logd("PrivateKeyCryptoProvider", "  Using signature as-is (${signatureBytes.size} bytes)")
                    signatureBytes
                }
                
                logd("PrivateKeyCryptoProvider", "  Generated signature (${finalSignature.size} bytes): ${finalSignature.take(32).joinToString("") { "%02x".format(it) }}...")
                return finalSignature
            }

            override suspend fun sign(bytes: ByteArray): ByteArray {
                logd("PrivateKeyCryptoProvider", "KMM Signer.sign(bytes) called")
                logd("PrivateKeyCryptoProvider", "  Address: $address")
                logd("PrivateKeyCryptoProvider", "  KeyIndex: $keyIndex")
                logd("PrivateKeyCryptoProvider", "  Bytes to sign (${bytes.size} bytes): ${bytes.take(50).joinToString("") { "%02x".format(it) }}...")
                logd("PrivateKeyCryptoProvider", "  Using signing algorithm: $signingAlgorithm")
                logd("PrivateKeyCryptoProvider", "  Using hashing algorithm: $hashingAlgorithm")
                
                val signatureBytes = privateKey.sign(bytes, signingAlgorithm, hashingAlgorithm)
                
                // Recovery ID trimming - restore this logic since Flow-Wallet-Kit isn't handling it properly
                // Remove recovery ID if present (Flow expects 64-byte signatures, not 65-byte with recovery ID)
                val finalSignature = if (signatureBytes.size == 65) {
                    logd("PrivateKeyCryptoProvider", "  Trimming recovery ID from 65-byte signature for $signingAlgorithm")
                    signatureBytes.copyOfRange(0, 64) // Remove the last byte (recovery ID)
                } else {
                    logd("PrivateKeyCryptoProvider", "  Using signature as-is (${signatureBytes.size} bytes)")
                    signatureBytes
                }
                
                logd("PrivateKeyCryptoProvider", "  Generated signature (${finalSignature.size} bytes): ${finalSignature.take(32).joinToString("") { "%02x".format(it) }}...")
                return finalSignature
            }
            
            override suspend fun signWithDomain(bytes: ByteArray, domain: ByteArray): ByteArray {
                logd("PrivateKeyCryptoProvider", "KMM Signer.signWithDomain() called")
                logd("PrivateKeyCryptoProvider", "  Domain: ${domain.joinToString("") { "%02x".format(it) }}")
                logd("PrivateKeyCryptoProvider", "  Bytes: ${bytes.take(32).joinToString("") { "%02x".format(it) }}...")
                return sign(domain + bytes)
            }
            
            override suspend fun signAsUser(bytes: ByteArray): ByteArray {
                logd("PrivateKeyCryptoProvider", "KMM Signer.signAsUser() called")
                return signWithDomain(bytes, DomainTag.User.bytes)
            }
            
            override suspend fun signAsTransaction(bytes: ByteArray): ByteArray {
                logd("PrivateKeyCryptoProvider", "KMM Signer.signAsTransaction() called")
                return signWithDomain(bytes, DomainTag.Transaction.bytes)
            }
        }
    }

    /**
     * Get the hashing algorithm used by this provider
     * Chooses the appropriate hashing algorithm based on the signing algorithm
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
     */
    override fun getSignatureAlgorithm(): SigningAlgorithm {
        return signingAlgorithm
    }

    /**
     * Get the key weight (default 1000 for full access)
     */
    override fun getKeyWeight(): Int {
        return 1000
    }

    /**
     * Get the associated KeyWallet if available
     */
    fun getKeyWallet(): KeyWallet? {
        return keyWallet
    }

    /**
     * Get the underlying PrivateKey
     */
    fun getPrivateKey(): PrivateKey {
        return privateKey
    }
} 