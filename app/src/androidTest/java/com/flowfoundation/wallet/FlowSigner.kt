package com.flowfoundation.wallet

import com.nftco.flow.sdk.Hasher
import com.nftco.flow.sdk.SignatureAlgorithm
import com.nftco.flow.sdk.Signer
import org.onflow.flow.models.HashingAlgorithm
import wallet.core.jni.Curve
import wallet.core.jni.Hash
import wallet.core.jni.PrivateKey

class WalletCoreSigner(
    private val privateKey: PrivateKey,
    private val signatureAlgo: SignatureAlgorithm,
    private val hashAlgo: HashingAlgorithm,
    override val hasher: Hasher = HasherImpl(hashAlgo)
) : Signer {

    override fun sign(bytes: ByteArray): ByteArray {
        val hashedData = hasher.hash(bytes)
        return when (signatureAlgo) {
            SignatureAlgorithm.ECDSA_P256 ->
                privateKey.sign(hashedData, Curve.NIST256P1).dropLast(1).toByteArray()
            SignatureAlgorithm.ECDSA_SECP256k1 ->
                privateKey.sign(hashedData, Curve.SECP256K1).dropLast(1).toByteArray()
            else -> ByteArray(0)
        }
    }
}

internal class HasherImpl(
    private val hashAlgo: HashingAlgorithm
) : Hasher {

    override fun hash(bytes: ByteArray): ByteArray {
        return when (hashAlgo) {
            HashingAlgorithm.SHA2_256 -> Hash.sha256(bytes)
            HashingAlgorithm.SHA3_256 -> Hash.sha3256(bytes)
            else -> ByteArray(0)
        }
    }
}