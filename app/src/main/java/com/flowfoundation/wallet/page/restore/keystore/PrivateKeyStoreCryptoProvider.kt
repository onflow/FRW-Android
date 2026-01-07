package com.flowfoundation.wallet.page.restore.keystore

import com.flow.wallet.CryptoProvider
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.keys.KeyFormat
import com.flowfoundation.wallet.page.restore.keystore.model.KeystoreAddress
import com.google.gson.Gson
import org.onflow.flow.models.DomainTag
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import com.flowfoundation.wallet.utils.Env.getStorage
import org.onflow.flow.models.hexToBytes
import com.flowfoundation.wallet.utils.logd
import org.onflow.flow.models.Transaction

// Add extension function for ByteArray to hex string conversion
fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
fun List<Byte>.toHexString(): String = joinToString("") { "%02x".format(it) }

class PrivateKeyStoreCryptoProvider(private val keystoreInfo: String) : CryptoProvider {
    private val TAG = "PrivateKeyStoreCryptoProvider"
    private val keyInfo: KeystoreAddress = Gson().fromJson(keystoreInfo, KeystoreAddress::class.java)
    private val signingAlgorithm = when (keyInfo.signAlgo) {
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
        logd(TAG, "Init keystore provider. signAlgo=${keyInfo.signAlgo}, hashAlgo=${keyInfo.hashAlgo}, address=${keyInfo.address}")
        logd(TAG, "KeyStore info details:")
        logd(TAG, "  Address: ${keyInfo.address}")
        logd(TAG, "  Public Key (from keystore): ${keyInfo.publicKey}")
        logd(TAG, "  Private Key loaded successfully")
        logd(TAG, "  Sign Algorithm: ${keyInfo.signAlgo} -> $signingAlgorithm")
        logd(TAG, "  Hash Algorithm: ${keyInfo.hashAlgo}")
        logd(TAG, "  Key ID: ${keyInfo.keyId}")
        logd(TAG, "  Weight: ${keyInfo.weight}")

        // Verify keystore consistency: check if private key generates the same public key
        verifyKeystoreConsistency()
    }

    private fun verifyKeystoreConsistency() {
        try {
            logd(TAG, "=== KEYSTORE CONSISTENCY VERIFICATION ===")
            val keystorePublicKey = keyInfo.publicKey
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
        val rawPublicKey = keyInfo.publicKey
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

        val hexSignature = signatureBytes.joinToString("") { String.format("%02x", it) }
        logd(TAG, "Final signature generated: $hexSignature")
        logd(TAG, "Final signature length: ${hexSignature.length} chars (${signatureBytes.size} bytes)")
        return hexSignature
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
            override var address: String = keyInfo.address
            override var keyIndex: Int = keyInfo.keyId

            override suspend fun sign(bytes: ByteArray, transaction: Transaction?): ByteArray {
                logd(TAG, "*** KEYSTORE SIGNER: sign() called - TRUSTWALLET CORE ***")
                logd(TAG, "  Address: $address")
                logd(TAG, "  KeyIndex: $keyIndex")
                logd(TAG, "  HashingAlgorithm: $hashingAlgorithm")
                logd(TAG, "  Input bytes length: ${bytes.size}")
                logd(TAG, "  Input bytes (first 32): ${bytes.take(32).toHexString()}")

                val signature = this@PrivateKeyStoreCryptoProvider.sign(bytes)
                logd(TAG, "  TrustWallet signature result: ${signature.toHexString()}")
                return signature
            }

            override suspend fun signWithDomain(bytes: ByteArray, domain: ByteArray, transaction: Transaction?): ByteArray {
                logd(TAG, "*** KEYSTORE SIGNER: signWithDomain() called - TRUSTWALLET CORE ***")
                logd(TAG, "  Domain: ${domain.toHexString()}")
                logd(TAG, "  Bytes: ${bytes.take(32).toHexString()}")
                return sign(domain + bytes, transaction)
            }

            override suspend fun signAsUser(bytes: ByteArray): ByteArray {
                logd(TAG, "*** KEYSTORE SIGNER: signAsUser() called - TRUSTWALLET CORE ***")
                return signWithDomain(bytes, DomainTag.User.bytes, null)
            }

            override suspend fun signAsTransaction(bytes: ByteArray, transaction: Transaction?): ByteArray {
                logd(TAG, "*** KEYSTORE SIGNER: signAsTransaction() called - TRUSTWALLET CORE ***")
                return signWithDomain(bytes, DomainTag.Transaction.bytes, transaction)
            }
        }
    }

    override fun getHashAlgorithm(): HashingAlgorithm {
        val code = keyInfo.hashAlgo
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
        return keyInfo.weight
    }
}
