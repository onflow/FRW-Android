package com.flowfoundation.wallet.manager.key

import com.flow.wallet.CryptoProvider
import com.flow.wallet.keys.SeedPhraseKey
import org.onflow.flow.models.DomainTag
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm

class HDWalletCryptoProvider(
    private val seedPhraseKey: SeedPhraseKey,
    private val signingAlgorithm: SigningAlgorithm = SigningAlgorithm.ECDSA_secp256k1,
    private val hashingAlgorithm: HashingAlgorithm = HashingAlgorithm.SHA2_256
) : CryptoProvider {

    fun getMnemonic(): String {
        return seedPhraseKey.mnemonic.joinToString(" ")
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun getPublicKey(): String {
        val rawPublicKey = seedPhraseKey.publicKey(signingAlgorithm)?.toHexString() ?: ""
        // Match the format used by all other crypto providers: remove "04" prefix if present, no "0x" prefix for server
        val formattedPublicKey = if (rawPublicKey.startsWith("04")) {
            rawPublicKey.substring(2)
        } else {
            rawPublicKey
        }
        return formattedPublicKey
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun getPrivateKey(): String {
        return seedPhraseKey.privateKey(signingAlgorithm)?.toHexString() ?: ""
    }

    override suspend fun getUserSignature(jwt: String): String {
        return signData(DomainTag.User.bytes + jwt.encodeToByteArray())
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun signData(data: ByteArray): String {
        val signatureBytes = seedPhraseKey.sign(data, signingAlgorithm, hashingAlgorithm)
        
        // Recovery ID trimming - ensure consistency with other providers
        // Remove recovery ID if present (Flow expects 64-byte signatures, not 65-byte with recovery ID)
        val finalSignature = if (signatureBytes.size == 65) {
            signatureBytes.copyOfRange(0, 64) // Remove the last byte (recovery ID)
        } else {
            signatureBytes
        }
        
        return finalSignature.toHexString()
    }

    override fun getSigner(hashingAlgorithm: HashingAlgorithm): org.onflow.flow.models.Signer {
        return object : org.onflow.flow.models.Signer {
            override var address: String = ""
            override var keyIndex: Int = 0
            
            override suspend fun sign(transaction: org.onflow.flow.models.Transaction?, bytes: ByteArray): ByteArray {
                val signature = seedPhraseKey.sign(bytes, signingAlgorithm, hashingAlgorithm)
                
                // Remove recovery ID if present (Flow expects 64-byte signatures, not 65-byte with recovery ID)
                val finalSignature = if (signature.size == 65) {
                    signature.copyOfRange(0, 64) // Remove the last byte (recovery ID)
                } else {
                    signature
                }
                
                return finalSignature
            }

            override suspend fun sign(bytes: ByteArray): ByteArray {
                val signature = seedPhraseKey.sign(bytes, signingAlgorithm, hashingAlgorithm)
                
                // Remove recovery ID if present (Flow expects 64-byte signatures, not 65-byte with recovery ID)
                val finalSignature = if (signature.size == 65) {
                    signature.copyOfRange(0, 64) // Remove the last byte (recovery ID)
                } else {
                    signature
                }
                
                return finalSignature
            }
            
            override suspend fun signWithDomain(bytes: ByteArray, domain: ByteArray): ByteArray {
                val signature = seedPhraseKey.sign(domain + bytes, signingAlgorithm, hashingAlgorithm)
                
                // Remove recovery ID if present (Flow expects 64-byte signatures, not 65-byte with recovery ID)
                val finalSignature = if (signature.size == 65) {
                    signature.copyOfRange(0, 64) // Remove the last byte (recovery ID)
                } else {
                    signature
                }
                
                return finalSignature
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
        return hashingAlgorithm
    }

    override fun getSignatureAlgorithm(): SigningAlgorithm {
        return signingAlgorithm
    }

    override fun getKeyWeight(): Int {
        return 1000
    }
}
