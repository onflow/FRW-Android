package com.flowfoundation.wallet.manager.key

import com.flow.wallet.CryptoProvider
import com.nftco.flow.sdk.HashAlgorithm
import com.nftco.flow.sdk.SignatureAlgorithm
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.Signer
import org.onflow.flow.models.SigningAlgorithm

class FlowWalletKitCryptoProvider(private val cryptoProvider: io.outblock.wallet.CryptoProvider) : CryptoProvider {
    override fun getPublicKey(): String {
        return cryptoProvider.getPublicKey()
    }

    override suspend fun getUserSignature(jwt: String): String {
        return cryptoProvider.getUserSignature(jwt)
    }

    override suspend fun signData(data: ByteArray): String {
        return cryptoProvider.signData(data)
    }

    override fun getSigner(): Signer {
        return when (cryptoProvider.getSignatureAlgorithm()) {
            SignatureAlgorithm.ECDSA_P256 -> Signer.ECDSA_P256
            SignatureAlgorithm.ECDSA_secp256k1 -> Signer.ECDSA_SECP256K1
            else -> throw IllegalArgumentException("Unsupported signature algorithm")
        }
    }

    override fun getHashAlgorithm(): HashingAlgorithm {
        return when (cryptoProvider.getHashAlgorithm()) {
            HashAlgorithm.SHA2_256 -> HashingAlgorithm.SHA2_256
            HashAlgorithm.SHA3_256 -> HashingAlgorithm.SHA3_256
            else -> throw IllegalArgumentException("Unsupported hash algorithm")
        }
    }

    override fun getSignatureAlgorithm(): SigningAlgorithm {
        return when (cryptoProvider.getSignatureAlgorithm()) {
            SignatureAlgorithm.ECDSA_P256 -> SigningAlgorithm.ECDSA_P256
            SignatureAlgorithm.ECDSA_secp256k1 -> SigningAlgorithm.ECDSA_SECP256K1
            else -> throw IllegalArgumentException("Unsupported signature algorithm")
        }
    }

    override fun getKeyWeight(): Int {
        return cryptoProvider.getKeyWeight()
    }
} 