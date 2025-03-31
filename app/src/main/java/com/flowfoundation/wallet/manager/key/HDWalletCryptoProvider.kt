package com.flowfoundation.wallet.manager.key

import com.flowfoundation.wallet.manager.flowjvm.transaction.checkSecurityProvider
import org.onflow.flow.models.DomainTag
import com.nftco.flow.sdk.Signer
import com.nftco.flow.sdk.bytesToHex
import com.nftco.flow.sdk.crypto.Crypto
import com.flowfoundation.wallet.manager.flowjvm.transaction.updateSecurityProvider
import io.outblock.wallet.CryptoProvider
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import wallet.core.jni.Curve
import wallet.core.jni.HDWallet
import wallet.core.jni.Hash

class HDWalletCryptoProvider(private val wallet: HDWallet) : CryptoProvider {

    fun getMnemonic(): String {
        return wallet.mnemonic()
    }

    override fun getPublicKey(): String {
        val privateKey = wallet.getCurveKey(Curve.SECP256K1, DERIVATION_PATH)
        val publicKey = privateKey.getPublicKeySecp256k1(false).data().bytesToHex()
        return publicKey.removePrefix("04")
    }

    fun getPrivateKey(): String {
        return wallet.getCurveKey(Curve.SECP256K1, DERIVATION_PATH).data().bytesToHex()
    }

    override fun getUserSignature(jwt: String): String {
        return signData(DomainTag.User.bytes + jwt.encodeToByteArray())
    }

    override fun signData(data: ByteArray): String {
        val privateKey = wallet.getCurveKey(Curve.SECP256K1, DERIVATION_PATH)
        val hashedData = Hash.sha256(data)
        val signature = privateKey.sign(hashedData, Curve.SECP256K1).dropLast(1).toByteArray()
        return signature.bytesToHex()
    }

    override fun getSigner(): Signer {
        checkSecurityProvider()
        updateSecurityProvider()
        return Crypto.getSigner( // to-do swap out this call to kmm
            privateKey = Crypto.decodePrivateKey(
                getPrivateKey(),
                getSignatureAlgorithm()
            ),
            hashAlgo = getHashAlgorithm()
        )
    }

    override fun getHashAlgorithm(): HashingAlgorithm {
        return HashingAlgorithm.SHA2_256
    }

    override fun getSignatureAlgorithm(): SigningAlgorithm {
        return SigningAlgorithm.ECDSA_secp256k1
    }

    override fun getKeyWeight(): Int {
        return 1000
    }

    companion object {
        private const val DERIVATION_PATH = "m/44'/539'/0'/0/0"
    }
}
