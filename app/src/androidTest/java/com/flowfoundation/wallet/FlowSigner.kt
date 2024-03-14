package com.flowfoundation.wallet

import com.nftco.flow.sdk.HashAlgorithm
import com.nftco.flow.sdk.Hasher
import com.nftco.flow.sdk.SignatureAlgorithm
import com.nftco.flow.sdk.Signer
import wallet.core.jni.Curve
import wallet.core.jni.Hash
import wallet.core.jni.PrivateKey

class WalletCoreSigner(
    private val privateKey: PrivateKey,
    private val signatureAlgo: SignatureAlgorithm,
    private val hashAlgo: HashAlgorithm,
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
    private val hashAlgo: HashAlgorithm
) : Hasher {

    override fun hash(bytes: ByteArray): ByteArray {
        return when (hashAlgo) {
            HashAlgorithm.SHA2_256 -> Hash.sha256(bytes)
            HashAlgorithm.SHA3_256 -> Hash.sha3256(bytes)
            else -> ByteArray(0)
        }
    }
}