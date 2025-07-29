package com.flowfoundation.wallet.manager.key

import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.storage.StorageProtocol
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import java.security.KeyStore
import java.security.KeyStore.PrivateKeyEntry
import java.security.interfaces.ECPrivateKey
import com.flow.wallet.keys.KeyFormat
import org.onflow.flow.models.SigningAlgorithm

/**
 * Handles backward compatibility between old Android Keystore pattern and new Flow-Wallet-Kit storage.
 *
 * Old pattern: user_keystore_{prefix} -> Android Keystore
 * New pattern: prefix_key_{prefix} -> Flow-Wallet-Kit storage
 */
object KeyCompatibilityManager {
    private const val TAG = "KeyCompatibility"
    private const val OLD_KEYSTORE_ALIAS_PREFIX = "user_keystore_"
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
        logd(TAG, "Attempting to get private key for prefix: $prefix")

        // First try the new storage pattern
        val newKeyId = "$NEW_STORAGE_KEY_PREFIX$prefix"
        val newStorageKey = tryGetFromNewStorage(newKeyId, prefix, storage)
        if (newStorageKey != null) {
            logd(TAG, "Successfully retrieved key from new storage: $newKeyId")
            return newStorageKey
        }

        logd(TAG, "Key not found in new storage, trying old Android Keystore pattern")

        // Fallback to old Android Keystore pattern
        val oldStorageKey = tryGetFromOldKeystore(prefix, storage)
        if (oldStorageKey != null) {
            logd(TAG, "Successfully retrieved key from old Android Keystore for prefix: $prefix")
            logd(TAG, "Note: Key accessed from old storage. Migration available via KeyStoreMigrationManager if needed.")
            return oldStorageKey
        }

        loge(TAG, "Private key not found in either new storage or old Android Keystore for prefix: $prefix")
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
     * Attempts to retrieve key from old Android Keystore and wrap it in PrivateKey
     */
    private fun tryGetFromOldKeystore(prefix: String, storage: StorageProtocol): PrivateKey? {
        return try {
            logd(TAG, "Trying to get key from old Android Keystore for prefix: $prefix")

            val oldAlias = "$OLD_KEYSTORE_ALIAS_PREFIX$prefix"
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)

            if (!keyStore.containsAlias(oldAlias)) {
                logd(TAG, "Old keystore alias not found: $oldAlias")
                return null
            }

            val keyEntry = keyStore.getEntry(oldAlias, null)
            if (keyEntry !is PrivateKeyEntry) {
                logd(TAG, "Keystore entry is not a PrivateKeyEntry: ${keyEntry?.javaClass?.simpleName}")
                return null
            }

            val privateKey = keyEntry.privateKey
            if (privateKey !is ECPrivateKey) {
                logd(TAG, "Private key is not an EC key: ${privateKey?.javaClass?.simpleName}")
                return null
            }

            // Extract raw private key bytes
            val privateKeyValue = privateKey.s
            val privateKeyBytes = privateKeyValue.toByteArray()

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

            logd(TAG, "Successfully extracted and converted old keystore key to PrivateKey")
            return newPrivateKey

        } catch (e: Exception) {
            loge(TAG, "Failed to get key from old Android Keystore: ${e.message}")
            null
        }
    }


    /**
     * Checks if a key exists in either storage system
     */
    fun hasPrivateKey(prefix: String, storage: StorageProtocol): Boolean {
        logd(TAG, "Checking if private key exists for prefix: $prefix")

        // Check new storage first
        val newKeyId = "$NEW_STORAGE_KEY_PREFIX$prefix"
        val hasNewKey = try {
            PrivateKey.get(newKeyId, prefix, storage)
            true
        } catch (e: Exception) {
            false
        }

        if (hasNewKey) {
            logd(TAG, "Key found in new storage")
            return true
        }

        // Check old Android Keystore
        val hasOldKey = try {
            val oldAlias = "$OLD_KEYSTORE_ALIAS_PREFIX$prefix"
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.containsAlias(oldAlias)
        } catch (e: Exception) {
            false
        }

        if (hasOldKey) {
            logd(TAG, "Key found in old Android Keystore")
            return true
        }

        logd(TAG, "Key not found in either storage system")
        return false
    }

    /**
     * Diagnostic method to list all keys in both storage systems
     */
    @OptIn(ExperimentalStdlibApi::class)
    fun diagnoseKeyStorage(prefix: String, storage: StorageProtocol): String {
        val report = StringBuilder()
        report.appendLine("=== Key Storage Diagnostic for prefix: $prefix ===")

        // Check new storage
        val newKeyId = "$NEW_STORAGE_KEY_PREFIX$prefix"
        try {
            val key = PrivateKey.get(newKeyId, prefix, storage)
            val publicKey = key.publicKey(SigningAlgorithm.ECDSA_P256)?.toHexString()
                ?: key.publicKey(SigningAlgorithm.ECDSA_secp256k1)?.toHexString()
            report.appendLine("✅ New storage ($newKeyId): Found (public key: ${publicKey?.take(16)}...)")
        } catch (e: Exception) {
            report.appendLine("❌ New storage ($newKeyId): Not found (${e.message})")
        }

        // Check old Android Keystore
        val oldAlias = "$OLD_KEYSTORE_ALIAS_PREFIX$prefix"
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)

            if (keyStore.containsAlias(oldAlias)) {
                val keyEntry = keyStore.getEntry(oldAlias, null)
                if (keyEntry is PrivateKeyEntry) {
                    report.appendLine("✅ Old Android Keystore ($oldAlias): Found (PrivateKeyEntry)")
                } else {
                    report.appendLine("⚠️ Old Android Keystore ($oldAlias): Found but wrong type (${keyEntry?.javaClass?.simpleName})")
                }
            } else {
                report.appendLine("❌ Old Android Keystore ($oldAlias): Not found")
            }
        } catch (e: Exception) {
            report.appendLine("❌ Old Android Keystore ($oldAlias): Error checking (${e.message})")
        }

        report.appendLine("=== End Diagnostic ===")
        return report.toString()
    }
}
