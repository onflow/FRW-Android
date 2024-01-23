package io.outblock.lilico.manager.backup

import com.nftco.flow.sdk.DomainTag
import com.nftco.flow.sdk.HashAlgorithm
import com.nftco.flow.sdk.SignatureAlgorithm
import com.nftco.flow.sdk.Signer
import com.nftco.flow.sdk.bytesToHex
import com.nftco.flow.sdk.crypto.Crypto
import io.outblock.lilico.manager.flowjvm.transaction.updateSecurityProvider
import io.outblock.wallet.CryptoProvider
import wallet.core.jni.Curve
import wallet.core.jni.HDWallet

class BackupCryptoProvider(private val wallet: HDWallet) : CryptoProvider {

    fun getMnemonic(): String {
        return wallet.mnemonic()
    }

    fun getKeyWeight(): Int {
        return 500
    }

    override fun getPublicKey(): String {
        val privateKey = wallet.getCurveKey(Curve.NIST256P1, DERIVATION_PATH)
        val publicKey = privateKey.publicKeyNist256p1.uncompressed().data().bytesToHex()
        return publicKey.removePrefix("04")
    }

    override fun getUserSignature(jwt: String): String {
        return signData(DomainTag.USER_DOMAIN_TAG + jwt.encodeToByteArray())
    }

    override fun signData(data: ByteArray): String {
        return getSigner().sign(data).bytesToHex()
    }

    override fun getSigner(): Signer {
//        updateSecurityProvider()
        return Crypto.getSigner(
            privateKey = Crypto.decodePrivateKey(
                wallet.getCurveKey(Curve.NIST256P1, DERIVATION_PATH).data().bytesToHex(),
                getSignatureAlgorithm()
            ),
            hashAlgo = getHashAlgorithm()
        )
    }

    override fun getHashAlgorithm(): HashAlgorithm {
        return HashAlgorithm.SHA2_256
    }

    override fun getSignatureAlgorithm(): SignatureAlgorithm {
        return SignatureAlgorithm.ECDSA_P256
    }

    companion object {
        private const val DERIVATION_PATH = "m/44'/539'/0'/0/0"
    }
}