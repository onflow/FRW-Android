package com.flowfoundation.wallet.manager.backup

import com.flow.wallet.CryptoProvider
import com.flow.wallet.keys.SeedPhraseKey
import org.onflow.flow.models.DomainTag
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.Signer
import org.onflow.flow.models.SigningAlgorithm

class BackupCryptoProvider(private val seedPhraseKey: SeedPhraseKey) : CryptoProvider {

    fun getMnemonic(): String {
        return seedPhraseKey.mnemonic.joinToString(" ")
    }

    override fun getKeyWeight(): Int {
        return 1000 // Standard key weight for Flow accounts
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun getPublicKey(): String {
        return seedPhraseKey.publicKey(SigningAlgorithm.ECDSA_P256)?.toHexString() ?: ""
    }

    override suspend fun getUserSignature(jwt: String): String {
        return signData(DomainTag.User.bytes + jwt.encodeToByteArray())
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun signData(data: ByteArray): String {
        return seedPhraseKey.sign(data, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA3_256).toHexString()
    }

    override fun getSigner(): Signer {
        return object : Signer {
            override var address: String = ""
            override var keyIndex: Int = 0
            
            override suspend fun sign(transaction: org.onflow.flow.models.Transaction?, bytes: ByteArray): ByteArray {
                return seedPhraseKey.sign(bytes, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA3_256)
            }

            override suspend fun sign(bytes: ByteArray): ByteArray {
                return seedPhraseKey.sign(bytes, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA3_256)
            }
        }
    }

    override fun getHashAlgorithm(): HashingAlgorithm {
        return HashingAlgorithm.SHA3_256 // Flow uses SHA3_256
    }

    override fun getSignatureAlgorithm(): SigningAlgorithm {
        return SigningAlgorithm.ECDSA_P256
    }
}