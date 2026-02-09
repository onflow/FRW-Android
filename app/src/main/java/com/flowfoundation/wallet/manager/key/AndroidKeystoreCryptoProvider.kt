package com.flowfoundation.wallet.manager.key

import com.flow.wallet.CryptoProvider
import com.flow.wallet.KeyManager
import com.flow.wallet.toFormatString
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.Signer
import org.onflow.flow.models.SigningAlgorithm
import org.onflow.flow.models.Transaction
import org.onflow.flow.models.bytesToHex
import java.security.PrivateKey
import java.security.Signature

/**
 * A CryptoProvider that works directly with hardware-backed Android Keystore keys
 * without attempting to extract the private key material.
 *
 * This provider is used for legacy accounts that have hardware-backed keys
 * stored in Android Keystore that cannot be migrated.
 */
class AndroidKeystoreCryptoProvider(
    private val keystorePrefix: String
) : CryptoProvider {

    private val TAG = "AndroidKeystoreCryptoProvider"

    private val privateKey: PrivateKey by lazy {
        try {
            KeyManager.getPrivateKeyByPrefix(keystorePrefix) ?: throw IllegalStateException("Private key not " +
              "found in Android Keystore")
        } catch (e: Exception) {
            throw IllegalStateException("Failed to load private key from Android Keystore: ${e.message}", e)
        }
    }

    private val cachedPublicKey: String by lazy {
        try {
            KeyManager.getPublicKeyByPrefix(keystorePrefix)?.toFormatString() ?: throw IllegalStateException("Public " + "key not found in Android Keystore")
        } catch (e: Exception) {
            throw IllegalStateException("Failed to load public key from Android Keystore: ${e.message}", e)
        }
    }

    override fun getPublicKey(): String {
        logd(TAG, "getPublicKey: $cachedPublicKey")
        return cachedPublicKey
    }

    override suspend fun getUserSignature(jwt: String): String {
        return getSigner(getHashAlgorithm()).signAsUser(
          jwt.encodeToByteArray()
        ).bytesToHex()
    }

    override suspend fun signData(data: ByteArray): String {
        return getSigner(getHashAlgorithm()).sign(data).bytesToHex()
    }

    override fun getSigner(hashingAlgorithm: HashingAlgorithm): Signer {
        return object : Signer {
            override var address: String = ""
            override var keyIndex: Int = 0

            override suspend fun sign(bytes: ByteArray, transaction: Transaction?): ByteArray {
                val signature = Signature.getInstance("SHA256withECDSA") //to-do: needs to be dynamic
                signature.initSign(privateKey)
                signature.update(bytes)
                val asn1Signature = signature.sign()
                try {
                    val seq = ASN1Sequence.getInstance(asn1Signature)
                    val r = (seq.getObjectAt(0) as ASN1Integer).value.toByteArray()
                    val s = (seq.getObjectAt(1) as ASN1Integer).value.toByteArray()
                    return (r.takeLast(32) + s.takeLast(32)).toByteArray()
                } catch (e: Exception) {
                    loge(TAG, "Failed to convert DER to raw signature: ${e.message}")

                    // Fallback: try to extract 64 bytes from the signature
                    return when {
                        asn1Signature.size >= 64 -> {
                            logd(TAG, "Using fallback: taking last 64 bytes")
                            asn1Signature.takeLast(64).toByteArray()
                        }
                        else -> {
                            logd(TAG, "Using fallback: padding to 64 bytes")
                            ByteArray(64).also { result ->
                              val copyLength = minOf(asn1Signature.size, 64)
                              System.arraycopy(asn1Signature, 0, result, 64 - copyLength, copyLength)
                            }
                        }
                    }
                }

            }

        }
    }

    override fun getHashAlgorithm(): HashingAlgorithm {
        return HashingAlgorithm.SHA2_256
    }

    override fun getSignatureAlgorithm(): SigningAlgorithm {
        return SigningAlgorithm.ECDSA_P256
    }

    override fun getKeyWeight(): Int {
        return 1000
    }

}
