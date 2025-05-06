package com.flowfoundation.wallet.manager.backup

import com.flow.wallet.CryptoProvider
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.crypto.DomainTag
import com.flow.wallet.crypto.HashingAlgorithm
import com.flow.wallet.crypto.Signer
import com.flow.wallet.crypto.SigningAlgorithm

class BackupCryptoProvider(private val seedPhraseKey: SeedPhraseKey) : CryptoProvider {

    fun getMnemonic(): String {
        return seedPhraseKey.mnemonic.joinToString(" ")
    }

    override fun getKeyWeight(): Int {
        return 500
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun getPublicKey(): String {
        return seedPhraseKey.publicKey(SigningAlgorithm.ECDSA_P256)?.toHexString()?.removePrefix("04") ?: ""
    }

    override suspend fun getUserSignature(jwt: String): String {
        return signData(DomainTag.User.bytes + jwt.encodeToByteArray())
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun signData(data: ByteArray): String {
        return seedPhraseKey.sign(data, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256).toHexString()
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun getSigner(): Signer {
        return seedPhraseKey.getSigner()
    }

    override fun getHashAlgorithm(): HashingAlgorithm {
        return HashingAlgorithm.SHA2_256
    }

    override fun getSignatureAlgorithm(): SigningAlgorithm {
        return SigningAlgorithm.ECDSA_P256
    }
}