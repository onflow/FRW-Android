package com.flowfoundation.wallet.manager.key

import com.flow.wallet.CryptoProvider
import com.flow.wallet.keys.SeedPhraseKey
import com.flowfoundation.wallet.network.model.EvmAccountInfo
import com.flowfoundation.wallet.utils.logd
import org.onflow.flow.models.DomainTag
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import org.onflow.flow.models.Transaction
import wallet.core.jni.Hash

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

            override suspend fun sign(bytes: ByteArray, transaction: Transaction?): ByteArray {
                val signature = seedPhraseKey.sign(bytes, signingAlgorithm, hashingAlgorithm)

                // Remove recovery ID if present (Flow expects 64-byte signatures, not 65-byte with recovery ID)
                val finalSignature = if (signature.size == 65) {
                    signature.copyOfRange(0, 64) // Remove the last byte (recovery ID)
                } else {
                    signature
                }

                return finalSignature
            }

            override suspend fun signWithDomain(bytes: ByteArray, domain: ByteArray, transaction: Transaction?): ByteArray {
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
                return signWithDomain(bytes, DomainTag.User.bytes, null)
            }

            override suspend fun signAsTransaction(bytes: ByteArray, transaction: Transaction?): ByteArray {
                return signWithDomain(bytes, DomainTag.Transaction.bytes, transaction)
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

    /**
     * Generates EVM account info by deriving the EVM key from the mnemonic and signing the JWT.
     * Used for v4 login/register endpoints that require EVM signatures.
     * 
     * @param jwt The Firebase JWT token to sign
     * @return EvmAccountInfo containing the EOA address and signature, or null if generation fails
     */
    fun getEvmAccountInfo(jwt: String): EvmAccountInfo? {
        return generateEvmAccountInfo(getMnemonic(), jwt)
    }

    companion object {
        private const val TAG = "HDWalletCryptoProvider"
        private const val EVM_DERIVATION_PATH = "m/44'/60'/0'/0/0"

        /**
         * Static helper to generate EVM account info from a raw mnemonic string.
         * Derives the EVM key using BIP44 path m/44'/60'/0'/0/0 and signs the JWT with keccak256.
         * 
         * @param mnemonic The mnemonic phrase (space-separated words)
         * @param jwt The Firebase JWT token to sign
         * @return EvmAccountInfo containing the EOA address and signature, or null if generation fails
         */
        fun generateEvmAccountInfo(mnemonic: String, jwt: String): EvmAccountInfo? {
            return try {
                val hdWallet = wallet.core.jni.HDWallet(mnemonic, "")
                val evmPrivateKey = hdWallet.getKeyByCurve(wallet.core.jni.Curve.SECP256K1, EVM_DERIVATION_PATH)
                val evmPublicKey = evmPrivateKey.getPublicKeySecp256k1(false)
                val evmAddress = wallet.core.jni.AnyAddress(evmPublicKey, wallet.core.jni.CoinType.ETHEREUM).description()
                val jwtHash = Hash.keccak256(jwt.toByteArray(Charsets.UTF_8))
                val signatureData = evmPrivateKey.sign(jwtHash, wallet.core.jni.Curve.SECP256K1)
                val evmSignature = "0x" + signatureData.joinToString("") { "%02x".format(it) }
                EvmAccountInfo(eoaAddress = evmAddress, signature = evmSignature)
            } catch (e: Exception) {
                logd(TAG, "Could not generate EVM account info: ${e.message}")
                null
            }
        }
    }
}
