package com.flowfoundation.wallet.manager.key

import com.flow.wallet.CryptoProvider
import com.flow.wallet.keys.SeedPhraseKey
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.Signer
import org.onflow.flow.models.SigningAlgorithm

class HDWalletCryptoProvider(private val seedPhraseKey: SeedPhraseKey) : CryptoProvider {

    fun getMnemonic(): String {
        return seedPhraseKey.mnemonic
    }

    override fun getPublicKey(): String {
        return seedPhraseKey.getPublicKey()
    }

    fun getPrivateKey(): String {
        return seedPhraseKey.privateKey
    }

    override suspend fun getUserSignature(jwt: String): String {
        return seedPhraseKey.getUserSignature(jwt)
    }

    override suspend fun signData(data: ByteArray): String {
        return seedPhraseKey.signData(data)
    }

    override fun getSigner(): Signer {
        return seedPhraseKey.getSigner()
    }

    override fun getHashAlgorithm(): HashingAlgorithm {
        return seedPhraseKey.getHashAlgorithm()
    }

    override fun getSignatureAlgorithm(): SigningAlgorithm {
        return seedPhraseKey.getSignatureAlgorithm()
    }

    override fun getKeyWeight(): Int {
        return seedPhraseKey.getKeyWeight()
    }
}
