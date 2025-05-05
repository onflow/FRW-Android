package com.flowfoundation.wallet.manager.backup

import com.flowfoundation.wallet.manager.flowjvm.transaction.checkSecurityProvider
import com.flow.wallet.CryptoProvider
import com.flow.wallet.keys.SeedPhraseKey
import org.onflow.flow.crypto.Crypto
import org.onflow.flow.models.DomainTag
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.Signer
import org.onflow.flow.models.SigningAlgorithm

class BackupCryptoProvider(private val seedPhraseKey: SeedPhraseKey) : CryptoProvider {

    fun getMnemonic(): String {
        return seedPhraseKey.mnemonic.joinToString(" ")
    }

    override fun getKeyWeight(): Int {
        return 500
    }

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
        checkSecurityProvider()
        return Crypto.getSigner(
            privateKey = Crypto.decodePrivateKey(
                seedPhraseKey.privateKey(SigningAlgorithm.ECDSA_P256)?.toHexString() ?: "",
                getSignatureAlgorithm()
            ),
            hashAlgo = getHashAlgorithm()
        )
    }

    override fun getHashAlgorithm(): HashingAlgorithm {
        return HashingAlgorithm.SHA2_256
    }

    override fun getSignatureAlgorithm(): SigningAlgorithm {
        return SigningAlgorithm.ECDSA_P256
    }
}