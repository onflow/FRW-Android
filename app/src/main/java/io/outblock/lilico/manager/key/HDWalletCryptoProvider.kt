package io.outblock.lilico.manager.key

import com.nftco.flow.sdk.DomainTag
import com.nftco.flow.sdk.HashAlgorithm
import com.nftco.flow.sdk.SignatureAlgorithm
import com.nftco.flow.sdk.Signer
import com.nftco.flow.sdk.bytesToHex
import com.nftco.flow.sdk.crypto.Crypto
import io.outblock.lilico.manager.flowjvm.transaction.checkSecurityProvider
import io.outblock.lilico.manager.flowjvm.transaction.updateSecurityProvider
import io.outblock.wallet.CryptoProvider
import wallet.core.jni.Curve
import wallet.core.jni.HDWallet
import wallet.core.jni.Hash

class HDWalletCryptoProvider(private val wallet: HDWallet) : CryptoProvider {

    override fun getPublicKey(): String {
        val privateKey = wallet.getCurveKey(Curve.SECP256K1, DERIVATION_PATH)
        val publicKey = privateKey.getPublicKeySecp256k1(false).data().bytesToHex()
        return publicKey.removePrefix("04")
    }

    fun getPrivateKey(): String {
        return wallet.getCurveKey(Curve.SECP256K1, DERIVATION_PATH).data().bytesToHex()
    }

    override fun getUserSignature(jwt: String): String {
        return signData(DomainTag.USER_DOMAIN_TAG + jwt.encodeToByteArray())
    }

    override fun signData(data: ByteArray): String {
        val privateKey = wallet.getCurveKey(Curve.SECP256K1, DERIVATION_PATH)
        val hashedData = Hash.sha256(data)
        val signature = privateKey.sign(hashedData, Curve.SECP256K1).dropLast(1).toByteArray()
        return signature.bytesToHex()
    }

    override fun getSigner(): Signer {
        updateSecurityProvider()
        return Crypto.getSigner(
            privateKey = Crypto.decodePrivateKey(
                getPrivateKey(),
                getSignatureAlgorithm()
            ),
            hashAlgo = getHashAlgorithm()
        )
    }

    override fun getHashAlgorithm(): HashAlgorithm {
        return HashAlgorithm.SHA2_256
    }

    override fun getSignatureAlgorithm(): SignatureAlgorithm {
        return SignatureAlgorithm.ECDSA_SECP256k1
    }

    companion object {
        private const val DERIVATION_PATH = "m/44'/539'/0'/0/0"
    }
}
