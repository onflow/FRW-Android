package com.flowfoundation.wallet.flowkit

import com.flow.wallet.keys.KeyProtocol
import com.flow.wallet.keys.KeyType
import com.flow.wallet.storage.StorageProtocol
import com.flowfoundation.wallet.manager.key.CryptoProvider
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm

/**
 * Adapter class to bridge between FRW-Android's crypto provider and Flow-Wallet-Kit's KeyProtocol
 */
class FlowKitKeyAdapter(
    private val cryptoProvider: CryptoProvider,
    override var storage: StorageProtocol
) : KeyProtocol {
    override val key: Any
        get() = cryptoProvider

    override val secret: ByteArray
        get() = cryptoProvider.getPrivateKey()?.toByteArray() ?: ByteArray(0)

    override val advance: Any
        get() = cryptoProvider

    override val keyType: KeyType
        get() = when {
            cryptoProvider is HDWalletCryptoProvider -> KeyType.SEED_PHRASE
            cryptoProvider is KeyStoreCryptoProvider -> KeyType.KEY_STORE
            cryptoProvider is PrivateKeyStoreCryptoProvider -> KeyType.PRIVATE_KEY
            else -> KeyType.PRIVATE_KEY
        }

    override suspend fun create(advance: Any, storage: StorageProtocol): KeyProtocol {
        throw NotImplementedError("Create with advance not supported")
    }

    override suspend fun create(storage: StorageProtocol): KeyProtocol {
        throw NotImplementedError("Create not supported")
    }

    override suspend fun get(id: String, password: String, storage: StorageProtocol): KeyProtocol {
        throw NotImplementedError("Get not supported")
    }

    override suspend fun restore(secret: ByteArray, storage: StorageProtocol): KeyProtocol {
        throw NotImplementedError("Restore not supported")
    }

    override fun publicKey(signAlgo: SigningAlgorithm): ByteArray? {
        return cryptoProvider.getPublicKey()?.toByteArray()
    }

    override fun privateKey(signAlgo: SigningAlgorithm): ByteArray? {
        return cryptoProvider.getPrivateKey()?.toByteArray()
    }

    override suspend fun sign(
        data: ByteArray,
        signAlgo: SigningAlgorithm,
        hashAlgo: HashingAlgorithm
    ): ByteArray {
        return cryptoProvider.sign(data, signAlgo.index, hashAlgo.index)
    }

    override fun isValidSignature(
        signature: ByteArray,
        message: ByteArray,
        signAlgo: SigningAlgorithm,
        hashAlgo: HashingAlgorithm
    ): Boolean {
        // TODO: Implement signature validation
        return false
    }

    override suspend fun store(id: String, password: String) {
        // TODO: Implement key storage
    }
} 