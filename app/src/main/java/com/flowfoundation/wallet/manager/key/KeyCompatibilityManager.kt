package com.flowfoundation.wallet.manager.key

import com.flow.wallet.KeyManager
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.storage.StorageProtocol
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import java.security.KeyStore
import java.security.KeyStore.PrivateKeyEntry
import java.security.interfaces.ECPrivateKey
import com.flow.wallet.keys.KeyFormat
import com.flowfoundation.wallet.manager.account.HardwareBackedKeyException

/**
 * Handles backward compatibility between old Android Keystore pattern and new Flow-Wallet-Kit storage.
 *
 * Old pattern: user_keystore_{prefix} -> Android Keystore
 * New pattern: prefix_key_{prefix} -> Flow-Wallet-Kit storage
 */
object KeyCompatibilityManager {
    private const val TAG = "KeyCompatibility"
    private const val NEW_STORAGE_KEY_PREFIX = "prefix_key_"

    /**
     * Attempts to get a private key with backward compatibility support.
     * First tries the new storage pattern, then falls back to old Android Keystore pattern.
     *
     * @param prefix The account prefix/password
     * @param storage The Flow-Wallet-Kit storage instance
     * @return PrivateKey instance or null if not found in either storage
     */
    fun getPrivateKeyWithFallback(prefix: String, storage: StorageProtocol): PrivateKey? {
        logd(TAG, "Attempting to get private key")

        // First try the new storage pattern
        val newKeyId = "$NEW_STORAGE_KEY_PREFIX$prefix"
        val newStorageKey = tryGetFromNewStorage(newKeyId, prefix, storage)
        if (newStorageKey != null) {
            logd(TAG, "Successfully retrieved key from new storage")
            return newStorageKey
        }

        logd(TAG, "Key not found in new storage, trying old Android Keystore pattern")

        // Fallback to old Android Keystore pattern
        val oldStorageKey = tryGetFromOldKeystore(prefix, storage)
        if (oldStorageKey != null) {
            logd(TAG, "Successfully retrieved key from old Android Keystore")
            logd(TAG, "Note: Key accessed from old storage. Migration available via KeyStoreMigrationManager if needed.")
            return oldStorageKey
        }

        loge(TAG, "Private key not found in either new storage or old Android Keystore")
        return null
    }

    /**
     * Attempts to retrieve key from new Flow-Wallet-Kit storage
     */
    private fun tryGetFromNewStorage(keyId: String, password: String, storage: StorageProtocol): PrivateKey? {
        return try {
            logd(TAG, "Trying to get key from new storage: keyId=$keyId")
            PrivateKey.get(keyId, password, storage)
        } catch (e: Exception) {
            logd(TAG, "Failed to get key from new storage: ${e.message}")
            null
        }
    }

    /**
     * Attempts to retrieve key from old Android Keystore
     * For hardware-backed keys, returns a special marker indicating that an
     * AndroidKeystoreCryptoProvider should be used instead
     */
    private fun tryGetFromOldKeystore(prefix: String, storage: StorageProtocol): PrivateKey? {
        return try {
            logd(TAG, "Trying to get key from old Android Keystore")

            val oldAlias = KeyManager.KEYSTORE_ALIAS_PREFIX + prefix
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)

            if (!keyStore.containsAlias(oldAlias)) {
                logd(TAG, "Old keystore alias not found")
                return null
            }

            val keyEntry = keyStore.getEntry(oldAlias, null)
            if (keyEntry !is PrivateKeyEntry) {
                logd(TAG, "Keystore entry is not a PrivateKeyEntry: ${keyEntry?.javaClass?.simpleName}")
                return null
            }

            val privateKey = keyEntry.privateKey
            logd(TAG, "Private key type: ${privateKey?.javaClass?.simpleName}")

            // Check if this is a hardware-backed key that cannot be extracted
            if (privateKey.javaClass.simpleName == "AndroidKeyStoreECPrivateKey") {
                // Try to determine if this is hardware-backed or software-backed
                val privateKeyBytes = extractFromAndroidKeyStoreECPrivateKey(privateKey)

                if (privateKeyBytes == null) {
                    // Hardware-backed key - cannot extract, need to use AndroidKeystoreCryptoProvider
                    loge(TAG, "Hardware-backed key detected")
                    loge(TAG, "Cannot extract private key material - this is a security feature")
                    loge(TAG, "Will need to use AndroidKeystoreCryptoProvider for this key")
                    throw HardwareBackedKeyException(prefix, "Hardware-backed key requires " + "AndroidKeystoreCryptoProvider")
                }

                // Software-backed key - proceed with extraction
                logd(TAG, "Software-backed AndroidKeyStore key detected, proceeding with extraction")

                // Normalize to 32 bytes
                val normalizedBytes = when {
                    privateKeyBytes.size == 32 -> privateKeyBytes
                    privateKeyBytes.size == 33 && privateKeyBytes[0] == 0.toByte() -> {
                        privateKeyBytes.copyOfRange(1, 33)
                    }
                    privateKeyBytes.size < 32 -> {
                        val padded = ByteArray(32)
                        System.arraycopy(privateKeyBytes, 0, padded, 32 - privateKeyBytes.size, privateKeyBytes.size)
                        padded
                    }
                    else -> {
                        loge(TAG, "Unexpected private key size: ${privateKeyBytes.size} bytes")
                        return null
                    }
                }

                // Create a new PrivateKey instance from the raw bytes
                val newPrivateKey = PrivateKey.create(storage)
                newPrivateKey.importPrivateKey(normalizedBytes, KeyFormat.RAW)

                logd(TAG, "Successfully extracted and converted software-backed keystore key to PrivateKey")
                return newPrivateKey
            }
            // Handle standard ECPrivateKey interface
            else if (privateKey is ECPrivateKey) {
                logd(TAG, "Using ECPrivateKey interface")
                val privateKeyBytes = privateKey.s.toByteArray()

                // Normalize to 32 bytes
                val normalizedBytes = when {
                    privateKeyBytes.size == 32 -> privateKeyBytes
                    privateKeyBytes.size == 33 && privateKeyBytes[0] == 0.toByte() -> {
                        privateKeyBytes.copyOfRange(1, 33)
                    }
                    privateKeyBytes.size < 32 -> {
                        val padded = ByteArray(32)
                        System.arraycopy(privateKeyBytes, 0, padded, 32 - privateKeyBytes.size, privateKeyBytes.size)
                        padded
                    }
                    else -> {
                        loge(TAG, "Unexpected private key size: ${privateKeyBytes.size} bytes")
                        return null
                    }
                }

                // Create a new PrivateKey instance from the raw bytes
                val newPrivateKey = PrivateKey.create(storage)
                newPrivateKey.importPrivateKey(normalizedBytes, KeyFormat.RAW)

                logd(TAG, "Successfully extracted and converted ECPrivateKey to PrivateKey")
                return newPrivateKey
            }
            else {
                loge(TAG, "Unsupported private key type: ${privateKey?.javaClass?.simpleName}")
                return null
            }

        } catch (e: HardwareBackedKeyException) {
            // Re-throw hardware-backed key exception for CryptoProviderManager to handle
            throw e
        } catch (e: Exception) {
            loge(TAG, "Failed to get key from old Android Keystore: ${e.message}")
            null
        }
    }

    /**
     * Attempts to extract private key bytes from AndroidKeyStoreECPrivateKey
     * Returns null for hardware-backed keys (which is expected and handled by throwing HardwareBackedKeyException)
     */
    private fun extractFromAndroidKeyStoreECPrivateKey(privateKey: java.security.PrivateKey): ByteArray? {
        logd(TAG, "Attempting to extract private key from AndroidKeyStoreECPrivateKey")

        // Check if this key implements ECPrivateKey interface (software-backed keys)
        if (privateKey is ECPrivateKey) {
            return try {
                logd(TAG, "AndroidKeyStore key implements ECPrivateKey interface - extracting (software-backed)")
                privateKey.s.toByteArray()
            } catch (e: Exception) {
                loge(TAG, "Failed to extract private key value: ${e.message}")
                null
            }
        }

        // Hardware-backed keys cannot be extracted - return null to trigger HardwareBackedKeyException
        logd(TAG, "AndroidKeyStore key is hardware-backed and cannot be extracted")
        logd(TAG, "This will trigger AndroidKeystoreCryptoProvider usage")

        return null
    }
}
