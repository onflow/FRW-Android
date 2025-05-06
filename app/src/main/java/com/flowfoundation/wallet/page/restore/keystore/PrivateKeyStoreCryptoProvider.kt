package com.flowfoundation.wallet.page.restore.keystore

import com.flow.wallet.CryptoProvider
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.keys.KeyFormat
import com.flow.wallet.storage.FileSystemStorage
import com.flowfoundation.wallet.page.restore.keystore.model.KeystoreAddress
import com.google.gson.Gson
import org.onflow.flow.models.DomainTag
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.Signer
import org.onflow.flow.models.SigningAlgorithm
import com.flowfoundation.wallet.utils.Env
import java.io.File

class PrivateKeyStoreCryptoProvider(private val keyStoreInfo: String): CryptoProvider {

    private var keyStoreAddress: KeystoreAddress = Gson().fromJson(keyStoreInfo, KeystoreAddress::class.java)
    private val privateKey: PrivateKey by lazy {
        val baseDir = File(Env.getApp().filesDir, "wallet")
        val storage = FileSystemStorage(baseDir)
        PrivateKey.create(storage).apply {
            importPrivateKey(keyStoreAddress.privateKey.toByteArray(), KeyFormat.HEX)
        }
    }

    fun getKeyStoreInfo(): String {
        return keyStoreInfo
    }

    fun getPrivateKey(): String {
        return keyStoreAddress.privateKey
    }

    fun getAddress(): String {
        return keyStoreAddress.address
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun getPublicKey(): String {
        return privateKey.publicKey(SigningAlgorithm.ECDSA_P256)?.toHexString()?.removePrefix("04") ?: ""
    }

    override suspend fun getUserSignature(jwt: String): String {
        return signData(DomainTag.User.bytes + jwt.encodeToByteArray())
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun signData(data: ByteArray): String {
        return privateKey.sign(data, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA3_256).toHexString()
    }

    override fun getSigner(): Signer {
        return object : Signer {
            override var address: String = keyStoreAddress.address
            override var keyIndex: Int = 0
            
            override suspend fun sign(transaction: org.onflow.flow.models.Transaction?, bytes: ByteArray): ByteArray {
                return privateKey.sign(bytes, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA3_256)
            }

            override suspend fun sign(bytes: ByteArray): ByteArray {
                return privateKey.sign(bytes, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA3_256)
            }
        }
    }

    override fun getHashAlgorithm(): HashingAlgorithm {
        return HashingAlgorithm.SHA3_256
    }

    override fun getSignatureAlgorithm(): SigningAlgorithm {
        return SigningAlgorithm.ECDSA_P256
    }

    override fun getKeyWeight(): Int {
        return keyStoreAddress.weight
    }
}