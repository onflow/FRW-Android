package com.flowfoundation.wallet.page.restore.keystore

import com.flowfoundation.wallet.manager.flowjvm.transaction.checkSecurityProvider
import com.flowfoundation.wallet.manager.flowjvm.transaction.updateSecurityProvider
import com.flowfoundation.wallet.page.restore.keystore.model.KeystoreAddress
import com.google.gson.Gson
import org.onflow.flow.models.DomainTag
import com.nftco.flow.sdk.HashAlgorithm
import com.nftco.flow.sdk.SignatureAlgorithm
import com.nftco.flow.sdk.Signer
import com.nftco.flow.sdk.bytesToHex
import com.nftco.flow.sdk.crypto.Crypto
import io.outblock.wallet.CryptoProvider


class PrivateKeyStoreCryptoProvider(private val keyStoreInfo: String): CryptoProvider {

    private var keyStoreAddress: KeystoreAddress = Gson().fromJson(keyStoreInfo, KeystoreAddress::class.java)

    fun getKeyStoreInfo(): String {
        return keyStoreInfo
    }

    fun getAddress(): String {
        return keyStoreAddress.address
    }

    override fun getPublicKey(): String {
        return keyStoreAddress.publicKey
    }

    override fun getUserSignature(jwt: String): String {
        return signData(DomainTag.User.bytes + jwt.encodeToByteArray())
    }

    override fun signData(data: ByteArray): String {
        return getSigner().sign(data).bytesToHex()
    }

    override fun getSigner(): Signer {
        checkSecurityProvider()
        updateSecurityProvider()
        return Crypto.getSigner(
            privateKey = Crypto.decodePrivateKey(
                keyStoreAddress.privateKey, getSignatureAlgorithm()
            ),
            hashAlgo = getHashAlgorithm()
        )
    }

    override fun getHashAlgorithm(): HashAlgorithm {
        return HashAlgorithm.fromCadenceIndex(keyStoreAddress.hashAlgo)
    }

    override fun getSignatureAlgorithm(): SignatureAlgorithm {
        return SignatureAlgorithm.fromCadenceIndex(keyStoreAddress.signAlgo)
    }

    override fun getKeyWeight(): Int {
        return keyStoreAddress.weight
    }
}