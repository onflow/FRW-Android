package com.flowfoundation.wallet.page.restore.keystore

import com.flow.wallet.CryptoProvider
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.keys.KeyFormat
import com.flowfoundation.wallet.page.restore.keystore.model.KeystoreAddress
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.onflow.flow.models.DomainTag
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.Signer
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
        return "0x${keyInfo.get("publicKey").asString}"
    }

    override suspend fun getUserSignature(jwt: String): String {
        return signData(DomainTag.User.bytes + jwt.encodeToByteArray())
    }

    override suspend fun signData(data: ByteArray): String {
        logd(TAG, "signData called. dataSize=${data.size} bytes, signAlgo=$signingAlgorithm, hashAlgo=${getHashAlgorithm()}")
        val signatureBytes = privateKey.sign(data, signingAlgorithm, getHashAlgorithm())
        logd(TAG, "Signature generated. size=${signatureBytes.size} bytes")
        return signatureBytes.joinToString("") { String.format("%02x", it) } 
    }

    // Helper method for signing raw bytes
    private suspend fun sign(data: ByteArray): ByteArray {
        logd(TAG, "[KEYSTORE] sign() input data (${data.size} bytes): ${data.take(32).toHexString()}...")
        logd(TAG, "[KEYSTORE] Using signAlgo: $signingAlgorithm, hashAlgo: ${getHashAlgorithm()}")
        
        val result = privateKey.sign(data, signingAlgorithm, getHashAlgorithm())
        
        logd(TAG, "[KEYSTORE] sign() result (${result.size} bytes): ${result.toHexString()}")
        return result
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