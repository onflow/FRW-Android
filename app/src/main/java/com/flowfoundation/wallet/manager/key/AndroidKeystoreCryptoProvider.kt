package com.flowfoundation.wallet.manager.key

import com.flow.wallet.CryptoProvider
import org.onflow.flow.models.DomainTag
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECPoint
import java.math.BigInteger

/**
 * A CryptoProvider that works directly with hardware-backed Android Keystore keys
 * without attempting to extract the private key material.
 * 
 * This provider is used for legacy accounts that have hardware-backed keys
 * stored in Android Keystore that cannot be migrated.
 */
class AndroidKeystoreCryptoProvider(
    private val keystoreAlias: String,
    private val signingAlgorithm: SigningAlgorithm = SigningAlgorithm.ECDSA_P256,
    private val hashingAlgorithm: HashingAlgorithm? = null
) : CryptoProvider {
    
    private val TAG = "AndroidKeystoreCryptoProvider"
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    }
    
    private val privateKey: PrivateKey by lazy {
        try {
            val keyEntry = keyStore.getEntry(keystoreAlias, null) as? KeyStore.PrivateKeyEntry
            keyEntry?.privateKey ?: throw IllegalStateException("Private key not found for alias: $keystoreAlias")
        } catch (e: Exception) {
            throw IllegalStateException("Failed to load private key from Android Keystore: ${e.message}", e)
        }
    }
    
    private val publicKey: PublicKey by lazy {
        try {
            val keyEntry = keyStore.getEntry(keystoreAlias, null) as? KeyStore.PrivateKeyEntry
            keyEntry?.certificate?.publicKey ?: throw IllegalStateException("Public key not found for alias: $keystoreAlias")
        } catch (e: Exception) {
            throw IllegalStateException("Failed to load public key from Android Keystore: ${e.message}", e)
        }
    }

    init {
        logd(TAG, "Initialized AndroidKeystoreCryptoProvider for alias: $keystoreAlias")
        logd(TAG, "Using signAlgo=$signingAlgorithm, hashAlgo=${hashingAlgorithm ?: "default"}")
        
        // Verify the key exists
        if (!keyStore.containsAlias(keystoreAlias)) {
            throw IllegalArgumentException("Keystore alias not found: $keystoreAlias")
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun getPublicKey(): String {
        return try {
            // Try to get the public key from the certificate first
            val pubKey = publicKey
            val encoded = pubKey.encoded
            
            // For ECDSA keys, the public key is typically the last 65 bytes (04 + 32 + 32)
            // or last 64 bytes if uncompressed without the 04 prefix
            val publicKeyBytes = if (encoded.size >= 65) {
                // Look for the uncompressed point (starts with 04)
                val startIndex = encoded.indexOfFirst { it == 0x04.toByte() }
                if (startIndex != -1 && startIndex + 65 <= encoded.size) {
                    encoded.copyOfRange(startIndex, startIndex + 65)
                } else {
                    // Fallback: take last 65 bytes
                    encoded.takeLast(65).toByteArray()
                }
            } else {
                encoded
            }
            
            val publicKeyHex = publicKeyBytes.toHexString()
            
            // Remove "04" prefix to match expected format (same as PrivateKeyStoreCryptoProvider)
            val formattedPublicKey = if (publicKeyHex.startsWith("04")) {
                publicKeyHex.substring(2)
            } else {
                publicKeyHex
            }
            
            logd(TAG, "Generated public key: ${formattedPublicKey.take(20)}...")
            formattedPublicKey
        } catch (e: Exception) {
            loge(TAG, "Failed to get public key: ${e.message}")
            throw RuntimeException("Failed to get public key from Android Keystore", e)
        }
    }

    override suspend fun getUserSignature(jwt: String): String {
        return signData(DomainTag.User.bytes + jwt.encodeToByteArray())
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun signData(data: ByteArray): String {
        return try {
            val effectiveHashingAlgorithm = hashingAlgorithm ?: getHashAlgorithm()
            val signatureAlgorithmName = getJavaSignatureAlgorithm(signingAlgorithm, effectiveHashingAlgorithm)
            
            logd(TAG, "Signing with algorithm: $signatureAlgorithmName")
            
            val signature = Signature.getInstance(signatureAlgorithmName)
            signature.initSign(privateKey)
            signature.update(data)
            val signatureBytes = signature.sign()
            
            // Convert DER signature to raw format expected by Flow
            val rawSignature = derToRaw(signatureBytes)
            
            logd(TAG, "Signature generated successfully: ${rawSignature.toHexString().take(20)}...")
            rawSignature.toHexString()
        } catch (e: Exception) {
            loge(TAG, "Failed to sign data: ${e.message}")
            throw RuntimeException("Failed to sign data with Android Keystore key", e)
        }
    }

    override fun getSigner(hashingAlgorithm: HashingAlgorithm): org.onflow.flow.models.Signer {
        return object : org.onflow.flow.models.Signer {
            override var address: String = ""
            override var keyIndex: Int = 0
            
            override suspend fun sign(transaction: org.onflow.flow.models.Transaction?, bytes: ByteArray): ByteArray {
                val signatureAlgorithmName = getJavaSignatureAlgorithm(signingAlgorithm, hashingAlgorithm)
                
                val signature = Signature.getInstance(signatureAlgorithmName)
                signature.initSign(privateKey)
                signature.update(bytes)
                val signatureBytes = signature.sign()
                
                return derToRaw(signatureBytes)
            }

            override suspend fun sign(bytes: ByteArray): ByteArray {
                return sign(null, bytes)
            }
            
            override suspend fun signWithDomain(bytes: ByteArray, domain: ByteArray): ByteArray {
                return sign(domain + bytes)
            }
            
            override suspend fun signAsUser(bytes: ByteArray): ByteArray {
                return signWithDomain(bytes, DomainTag.User.bytes)
            }
            
            override suspend fun signAsTransaction(bytes: ByteArray): ByteArray {
                return signWithDomain(bytes, DomainTag.Transaction.bytes)
            }
        }
    }

    override fun getHashAlgorithm(): HashingAlgorithm {
        return hashingAlgorithm ?: run {
            when (signingAlgorithm) {
                SigningAlgorithm.ECDSA_secp256k1 -> HashingAlgorithm.SHA2_256
                SigningAlgorithm.ECDSA_P256 -> HashingAlgorithm.SHA3_256
                else -> HashingAlgorithm.SHA3_256
            }
        }
    }

    override fun getSignatureAlgorithm(): SigningAlgorithm {
        return signingAlgorithm
    }

    override fun getKeyWeight(): Int {
        return 1000
    }
    
    /**
     * Get the Java signature algorithm name for the given signing and hashing algorithms
     */
    private fun getJavaSignatureAlgorithm(signingAlgorithm: SigningAlgorithm, hashingAlgorithm: HashingAlgorithm): String {
        val hashName = when (hashingAlgorithm) {
            HashingAlgorithm.SHA2_256 -> "SHA256"
            HashingAlgorithm.SHA3_256 -> "SHA256" // Android Keystore doesn't support SHA3, use SHA2
            else -> "SHA256"
        }
        
        return when (signingAlgorithm) {
            SigningAlgorithm.ECDSA_P256 -> "${hashName}withECDSA"
            SigningAlgorithm.ECDSA_secp256k1 -> "${hashName}withECDSA" 
            else -> "${hashName}withECDSA"
        }
    }
    
    /**
     * Convert DER-encoded signature to raw format (r || s)
     * Android Keystore returns signatures in DER format, but Flow expects 64-byte raw format
     * Based on the working PrivateKeyStoreCryptoProvider pattern
     */
    private fun derToRaw(derSignature: ByteArray): ByteArray {
        try {
            logd(TAG, "DER signature input: ${derSignature.size} bytes")
            
            // If it's already 64 bytes, assume it's raw format
            if (derSignature.size == 64) {
                logd(TAG, "Signature is already 64 bytes, assuming raw format")
                return derSignature
            }
            
            // If it's 65 bytes, remove recovery ID (like PrivateKeyStoreCryptoProvider does)
            if (derSignature.size == 65) {
                logd(TAG, "Trimming recovery ID from 65-byte signature")
                return derSignature.copyOfRange(0, 64)
            }
            
            // Parse DER format: 0x30 [total-length] 0x02 [R-length] [R] 0x02 [S-length] [S]
            var offset = 0
            
            // Skip sequence tag (0x30) and length
            if (derSignature[offset] != 0x30.toByte()) {
                throw IllegalArgumentException("Invalid DER signature: missing sequence tag")
            }
            offset++ // Skip 0x30
            
            // Skip length byte(s) - handle both short and long form
            val lengthByte = derSignature[offset].toInt() and 0xFF
            if (lengthByte and 0x80 == 0) {
                offset++ // Short form: single byte length
            } else {
                val lengthBytes = lengthByte and 0x7F
                offset += 1 + lengthBytes // Long form: skip length indicator + length bytes
            }
            
            // Read R
            if (derSignature[offset] != 0x02.toByte()) {
                throw IllegalArgumentException("Invalid DER signature: missing R integer tag")
            }
            offset++ // Skip 0x02
            val rLength = derSignature[offset].toInt() and 0xFF
            offset++ // Skip length byte
            
            val rBytes = derSignature.copyOfRange(offset, offset + rLength)
            offset += rLength
            
            // Read S
            if (derSignature[offset] != 0x02.toByte()) {
                throw IllegalArgumentException("Invalid DER signature: missing S integer tag")
            }
            offset++ // Skip 0x02
            val sLength = derSignature[offset].toInt() and 0xFF
            offset++ // Skip length byte
            
            val sBytes = derSignature.copyOfRange(offset, offset + sLength)
            
            // Normalize to 32 bytes each (remove leading zeros, pad if needed)
            val rNormalized = normalizeToSize(rBytes, 32)
            val sNormalized = normalizeToSize(sBytes, 32)
            
            val result = rNormalized + sNormalized
            logd(TAG, "DER conversion successful: ${result.size} bytes")
            return result
            
        } catch (e: Exception) {
            loge(TAG, "Failed to convert DER to raw signature: ${e.message}")
            
            // Fallback: try to extract 64 bytes from the signature
            return when {
                derSignature.size >= 64 -> {
                    logd(TAG, "Using fallback: taking last 64 bytes")
                    derSignature.takeLast(64).toByteArray()
                }
                else -> {
                    logd(TAG, "Using fallback: padding to 64 bytes")
                    ByteArray(64).also { result ->
                        val copyLength = minOf(derSignature.size, 64)
                        System.arraycopy(derSignature, 0, result, 64 - copyLength, copyLength)
                    }
                }
            }
        }
    }
    
    /**
     * Normalize byte array to specific size by removing leading zeros or padding
     */
    private fun normalizeToSize(bytes: ByteArray, targetSize: Int): ByteArray {
        return when {
            bytes.size == targetSize -> bytes
            bytes.size > targetSize -> {
                // Remove leading zeros
                val firstNonZero = bytes.indexOfFirst { it != 0.toByte() }
                if (firstNonZero == -1) {
                    // All zeros
                    ByteArray(targetSize)
                } else {
                    val significant = bytes.copyOfRange(firstNonZero, bytes.size)
                    if (significant.size <= targetSize) {
                        ByteArray(targetSize - significant.size) + significant
                    } else {
                        significant.takeLast(targetSize).toByteArray()
                    }
                }
            }
            else -> {
                // Pad with leading zeros
                ByteArray(targetSize - bytes.size) + bytes
            }
        }
    }
}