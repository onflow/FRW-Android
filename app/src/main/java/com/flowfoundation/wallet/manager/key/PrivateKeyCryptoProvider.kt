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
    private val signingAlgorithm: SigningAlgorithm = SigningAlgorithm.ECDSA_P256
) : CryptoProvider {
    
    private val TAG = "PrivateKeyCryptoProvider"

    init {
        logd(TAG, "Init private key provider with signAlgo=$signingAlgorithm")
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
        logd(TAG, "signData called. dataSize=${data.size} bytes, signAlgo=$signingAlgorithm, hashAlgo=${getHashAlgorithm()}")
        val signatureBytes = privateKey.sign(data, signingAlgorithm, getHashAlgorithm())
        logd(TAG, "Signature generated. size=${signatureBytes.size} bytes")
        return signatureBytes.toHexString()
    }

    override fun getSigner(hashingAlgorithm: HashingAlgorithm): org.onflow.flow.models.Signer {
        return object : org.onflow.flow.models.Signer {
            override var address: String = ""
            override var keyIndex: Int = 0
            
            override suspend fun sign(transaction: org.onflow.flow.models.Transaction?, bytes: ByteArray): ByteArray {
                return privateKey.sign(bytes, signingAlgorithm, hashingAlgorithm)
            }

            override suspend fun sign(bytes: ByteArray): ByteArray {
                return privateKey.sign(bytes, signingAlgorithm, hashingAlgorithm)
            }
        }
    }

    /**
     * Get the hashing algorithm used by this provider
     * Using SHA2_256 to match Flow blockchain standards
     */
    override fun getHashAlgorithm(): HashingAlgorithm {
        return HashingAlgorithm.SHA2_256
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