package com.flowfoundation.wallet.page.restore.keystore

import com.flow.wallet.CryptoProvider
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.keys.KeyFormat
import com.flowfoundation.wallet.page.restore.keystore.model.KeystoreAddress
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.onflow.flow.models.DomainTag
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import com.flowfoundation.wallet.utils.Env.getStorage
import org.onflow.flow.models.hexToBytes
import com.flowfoundation.wallet.utils.logd

// Add extension function for ByteArray to hex string conversion
fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
fun List<Byte>.toHexString(): String = joinToString("") { "%02x".format(it) }

class PrivateKeyStoreCryptoProvider(private val keystoreInfo: String) : CryptoProvider {
    private val TAG = "PrivateKeyStoreCryptoProvider"
    private val keyInfo: JsonObject = Gson().fromJson(keystoreInfo, JsonObject::class.java)
    private val signingAlgorithm = when (keyInfo.get("signAlgo").asInt) {
        1 -> SigningAlgorithm.ECDSA_P256
        2 -> SigningAlgorithm.ECDSA_secp256k1
        else -> SigningAlgorithm.ECDSA_P256
    }

    private var keyStoreAddress: KeystoreAddress = Gson().fromJson(keystoreInfo, KeystoreAddress::class.java)
    private val privateKey: PrivateKey by lazy {
        val storage = getStorage()
        PrivateKey.create(storage).apply {
            val keyBytes = keyStoreAddress.privateKey
                .removePrefix("0x")
                .hexToBytes()
            importPrivateKey(keyBytes, KeyFormat.RAW)
        }
    }

    init {
        logd(TAG, "Init keystore provider. signAlgo=${keyInfo.get("signAlgo").asInt}, hashAlgo=${keyInfo.get("hashAlgo").asInt}, address=${keyInfo.get("address").asString}")
        logd(TAG, "KeyStore info details:")
        logd(TAG, "  Address: ${keyInfo.get("address").asString}")
        logd(TAG, "  Public Key (from keystore): ${keyInfo.get("publicKey").asString}")
        logd(TAG, "  Private Key loaded successfully")
        logd(TAG, "  Sign Algorithm: ${keyInfo.get("signAlgo").asInt} -> $signingAlgorithm")
        logd(TAG, "  Hash Algorithm: ${keyInfo.get("hashAlgo").asInt}")
        logd(TAG, "  Key ID: ${keyInfo.get("keyId").asInt}")
        logd(TAG, "  Weight: ${keyInfo.get("weight").asInt}")
        
        // Verify keystore consistency: check if private key generates the same public key
        verifyKeystoreConsistency()
    }
    
    private fun verifyKeystoreConsistency() {
        try {
            logd(TAG, "=== KEYSTORE CONSISTENCY VERIFICATION ===")
            val keystorePublicKey = keyInfo.get("publicKey").asString
            logd(TAG, "Keystore stored public key: $keystorePublicKey")
            
            // Get the public key derived from the private key
            val derivedPublicKey = privateKey.publicKey(signingAlgorithm)?.toHexString()
            logd(TAG, "Public key derived from private key: $derivedPublicKey")
            
            if (derivedPublicKey != null) {
                val keystoreClean = keystorePublicKey.removePrefix("0x").lowercase()
                val derivedClean = derivedPublicKey.removePrefix("0x").lowercase()
                
                // Handle potential "04" prefix for uncompressed keys
                val derivedStripped = if (derivedClean.startsWith("04") && derivedClean.length == 130) {
                    derivedClean.substring(2)
                } else {
                    derivedClean
                }
                
                logd(TAG, "Keystore public key (clean): $keystoreClean")
                logd(TAG, "Derived public key (clean): $derivedClean") 
                logd(TAG, "Derived public key (stripped): $derivedStripped")
                
                val match = keystoreClean == derivedClean || keystoreClean == derivedStripped
                logd(TAG, "Public key consistency check: ${if (match) "PASS" else "FAIL"}")
                
                if (!match) {
                    logd(TAG, "ERROR: Private key does not generate the public key stored in keystore!")
                    logd(TAG, "This indicates corrupted keystore data or incorrect key derivation.")
                }
            } else {
                logd(TAG, "ERROR: Could not derive public key from private key")
            }
            
            logd(TAG, "=== END KEYSTORE CONSISTENCY VERIFICATION ===")
        } catch (e: Exception) {
            logd(TAG, "Exception during keystore consistency verification: ${e.message}")
        }
    }

    fun getKeyStoreInfo(): String {
        return keystoreInfo
    }

    fun getPrivateKey(): String {
        return keyStoreAddress.privateKey
    }

    fun getAddress(): String {
        return keyStoreAddress.address
    }

    override fun getPublicKey(): String {
        val rawPublicKey = keyInfo.get("publicKey").asString
        // Match the format used in keystore restore: remove "04" prefix if present, no "0x" prefix for server
        val formattedPublicKey = if (rawPublicKey.startsWith("04")) {
            rawPublicKey.substring(2)
        } else {
            rawPublicKey
        }
        logd(TAG, "getPublicKey() -> formatting '$rawPublicKey' to '$formattedPublicKey' (no 0x prefix for server)")
        return formattedPublicKey
    }

    override suspend fun getUserSignature(jwt: String): String {
        logd(TAG, "getUserSignature called with JWT length: ${jwt.length}")
        return signData(DomainTag.User.bytes + jwt.encodeToByteArray())
    }

    override suspend fun signData(data: ByteArray): String {
        logd(TAG, "signData called. dataSize=${data.size} bytes, signAlgo=$signingAlgorithm, hashAlgo=${getHashAlgorithm()}")
        logd(TAG, "Data to sign (first 32 bytes): ${data.take(32).joinToString("") { "%02x".format(it) }}")
        
        val signatureBytes = privateKey.sign(data, signingAlgorithm, getHashAlgorithm())
        logd(TAG, "Raw signature from privateKey.sign(): size=${signatureBytes.size} bytes")
        
        // Recovery ID trimming - ensure consistency with account switching flow
        // Remove recovery ID if present (Flow expects 64-byte signatures, not 65-byte with recovery ID)
        val finalSignature = if (signatureBytes.size == 65) {
            logd(TAG, "Trimming recovery ID from 65-byte signature for $signingAlgorithm")
            signatureBytes.copyOfRange(0, 64) // Remove the last byte (recovery ID)
        } else {
            logd(TAG, "Using signature as-is (${signatureBytes.size} bytes)")
            signatureBytes
        }
        
        val hexSignature = finalSignature.joinToString("") { String.format("%02x", it) }
        logd(TAG, "Final signature generated: $hexSignature")
        logd(TAG, "Final signature length: ${hexSignature.length} chars (${finalSignature.size} bytes)")
        return hexSignature
    }

    // Helper method for signing raw bytes
    private suspend fun sign(data: ByteArray): ByteArray {
        logd(TAG, "[KEYSTORE] sign() input data (${data.size} bytes): ${data.take(32).toHexString()}...")
        logd(TAG, "[KEYSTORE] Using signAlgo: $signingAlgorithm, hashAlgo: ${getHashAlgorithm()}")
        
        val result = privateKey.sign(data, signingAlgorithm, getHashAlgorithm())
        
        // Recovery ID trimming - ensure consistency with account switching flow
        // Remove recovery ID if present (Flow expects 64-byte signatures, not 65-byte with recovery ID)
        val finalResult = if (result.size == 65) {
            logd(TAG, "[KEYSTORE] Trimming recovery ID from 65-byte signature for $signingAlgorithm")
            result.copyOfRange(0, 64) // Remove the last byte (recovery ID)
        } else {
            logd(TAG, "[KEYSTORE] Using signature as-is (${result.size} bytes)")
            result
        }
        
        logd(TAG, "[KEYSTORE] sign() result (${finalResult.size} bytes): ${finalResult.toHexString()}")
        return finalResult
    }

    override fun getSigner(hashingAlgorithm: HashingAlgorithm): org.onflow.flow.models.Signer {
        logd(TAG, "getSigner() called with hashingAlgorithm: $hashingAlgorithm")
        return object : org.onflow.flow.models.Signer {
            override var address: String = keyInfo.get("address").asString
            override var keyIndex: Int = keyInfo.get("keyId").asInt
            
            override suspend fun sign(transaction: org.onflow.flow.models.Transaction?, bytes: ByteArray): ByteArray {
                logd(TAG, "*** KEYSTORE SIGNER: sign(transaction, bytes) called - TRUSTWALLET CORE ***")
                logd(TAG, "  Address: $address")
                logd(TAG, "  KeyIndex: $keyIndex")
                logd(TAG, "  HashingAlgorithm: $hashingAlgorithm")
                logd(TAG, "  Input bytes length: ${bytes.size}")
                logd(TAG, "  Input bytes (first 32): ${bytes.take(32).toHexString()}")
                
                val signature = this@PrivateKeyStoreCryptoProvider.sign(bytes)
                logd(TAG, "  TrustWallet signature result: ${signature.toHexString()}")
                return signature
            }
            
            override suspend fun sign(bytes: ByteArray): ByteArray {
                logd(TAG, "*** KEYSTORE SIGNER: sign(bytes) called - TRUSTWALLET CORE ***")
                logd(TAG, "  Address: $address")
                logd(TAG, "  KeyIndex: $keyIndex") 
                logd(TAG, "  HashingAlgorithm: $hashingAlgorithm")
                logd(TAG, "  Input bytes length: ${bytes.size}")
                logd(TAG, "  Input bytes (first 32): ${bytes.take(32).toHexString()}")
                
                val signature = this@PrivateKeyStoreCryptoProvider.sign(bytes)
                logd(TAG, "  TrustWallet signature result: ${signature.toHexString()}")
                return signature
            }
            
            override suspend fun signWithDomain(bytes: ByteArray, domain: ByteArray): ByteArray {
                logd(TAG, "*** KEYSTORE SIGNER: signWithDomain() called - TRUSTWALLET CORE ***")
                logd(TAG, "  Domain: ${domain.toHexString()}")
                logd(TAG, "  Bytes: ${bytes.take(32).toHexString()}")
                return sign(domain + bytes)
            }
            
            override suspend fun signAsUser(bytes: ByteArray): ByteArray {
                logd(TAG, "*** KEYSTORE SIGNER: signAsUser() called - TRUSTWALLET CORE ***") 
                return signWithDomain(bytes, DomainTag.User.bytes)
            }
            
            override suspend fun signAsTransaction(bytes: ByteArray): ByteArray {
                logd(TAG, "*** KEYSTORE SIGNER: signAsTransaction() called - TRUSTWALLET CORE ***")
                return signWithDomain(bytes, DomainTag.Transaction.bytes)
            }
        }
    }

    override fun getHashAlgorithm(): HashingAlgorithm {
        val code = keyInfo.get("hashAlgo").asInt
        val algo = when (code) {
            1 -> HashingAlgorithm.SHA2_256  // SHA2-256
            2 -> HashingAlgorithm.SHA2_256  // Legacy value for SHA2-256
            3 -> HashingAlgorithm.SHA3_256
            else -> HashingAlgorithm.SHA2_256
        }
        logd(TAG, "hashAlgo mapping. code=$code -> $algo")
        return algo
    }

    override fun getSignatureAlgorithm(): SigningAlgorithm {
        return signingAlgorithm
    }

    override fun getKeyWeight(): Int {
        return keyInfo.get("weight").asInt
    }
}