package com.flowfoundation.wallet.manager.account

import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.keys.KeyFormat
import com.flow.wallet.storage.StorageProtocol
import com.flowfoundation.wallet.cache.AccountCacheManager
import com.flowfoundation.wallet.utils.Env.getStorage
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logw
import org.onflow.flow.models.SigningAlgorithm
import java.security.KeyStore
import java.security.KeyStore.PrivateKeyEntry
import java.security.interfaces.ECPrivateKey

/**
 * Manages migration of private keys from the old Android Keystore system
 * to the new Flow-Wallet-Kit storage system.
 * 
 * This is critical for users upgrading from versions that used direct Android Keystore access
 * to versions that use the Flow-Wallet-Kit storage abstraction.
 */
object KeyStoreMigrationManager {
    private const val TAG = "KeyStoreMigration"
    private const val OLD_KEYSTORE_ALIAS_PREFIX = "user_keystore_"
    private const val MIGRATION_COMPLETED_KEY = "keystore_migration_completed"
    
    /**
     * Performs migration of private keys from old Android Keystore to new storage system.
     * This should be called during app startup before any wallet operations.
     */
    suspend fun performMigrationIfNeeded() {
        logd(TAG, "=== Starting KeyStore Migration Check ===")
        
        try {
            // Check if migration has already been completed
            if (isMigrationCompleted()) {
                logd(TAG, "Migration already completed, skipping.")
                return
            }
            
            // Get all accounts that might need migration
            val accounts = AccountCacheManager.read() ?: emptyList()
            logd(TAG, "Found ${accounts.size} accounts to check for migration")
            
            val storage = getStorage()
            var migrationPerformed = false
            
            // Check each account
            for (account in accounts) {
                val prefix = account.prefix
                if (!prefix.isNullOrBlank()) {
                    logd(TAG, "Checking account with prefix: $prefix")
                    
                    // Check if the account already has a key in the new storage system
                    val newKeyId = "prefix_key_$prefix"
                    if (hasKeyInNewStorage(newKeyId, prefix, storage)) {
                        logd(TAG, "Account $prefix already has key in new storage, skipping")
                        continue
                    }
                    
                    // Attempt to migrate the key from old Android Keystore
                    val oldAlias = OLD_KEYSTORE_ALIAS_PREFIX + prefix
                    try {
                        logd(TAG, "Attempting to extract key from old keystore for prefix: $prefix")
                        val privateKeyData = extractPrivateKeyFromAndroidKeystore(oldAlias)
                        
                        logd(TAG, "Found private key in old keystore for prefix: $prefix")
                        
                        // Migrate the key to the new storage system
                        migratePrivateKey(privateKeyData, newKeyId, prefix, storage)
                        logd(TAG, "Successfully migrated key for prefix: $prefix")
                        migrationPerformed = true
                        
                    } catch (e: KeystoreKeyNotFoundException) {
                        logd(TAG, "No key found in old keystore for prefix: $prefix (not an error - may be new account)")
                    } catch (e: InvalidPrivateKeySizeException) {
                        loge(TAG, "CRITICAL: Invalid private key size for prefix $prefix: ${e.message}")
                        loge(TAG, "Key details: actualSize=${e.keyBytes?.size}, keyBytes=${e.getKeyBytesHex()?.take(64)}...")
                        loge(TAG, "This account may require manual recovery or the key may be corrupted")
                    } catch (e: KeyStoreMigrationException) {
                        loge(TAG, "Migration failed for prefix $prefix: $e")
                        loge(TAG, "Account may need manual intervention or alternative recovery method")
                    } catch (e: Exception) {
                        loge(TAG, "Unexpected error migrating key for prefix $prefix: ${e.message}")
                    }
                }
            }
            
            // Mark migration as completed if any migration was performed or no migration was needed
            if (migrationPerformed || accounts.isEmpty()) {
                markMigrationCompleted()
                logd(TAG, "Migration process completed successfully")
            }
            
        } catch (e: Exception) {
            logd(TAG, "Error during migration: ${e.message}")
            // Don't mark as completed if there was an error
        }
        
        logd(TAG, "=== KeyStore Migration Check Completed ===")
    }
    
    /**
     * Checks if a key exists in the new storage system
     */
    private fun hasKeyInNewStorage(keyId: String, password: String, storage: StorageProtocol): Boolean {
        return try {
            PrivateKey.get(keyId, password, storage)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Extracts private key data from the old Android Keystore system
     * @throws KeyStoreMigrationException with specific details about the failure
     */
    private fun extractPrivateKeyFromAndroidKeystore(alias: String): ByteArray {
        try {
            logd(TAG, "Attempting to extract key from Android Keystore with alias: $alias")
            
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            
            if (!keyStore.containsAlias(alias)) {
                throw KeystoreKeyNotFoundException(alias)
            }
            
            val keyEntry = keyStore.getEntry(alias, null)
            if (keyEntry !is PrivateKeyEntry) {
                val entryType = keyEntry?.javaClass?.simpleName ?: "null"
                throw InvalidKeystoreEntryException(alias, entryType)
            }
            
            val privateKey = keyEntry.privateKey
            if (privateKey !is ECPrivateKey) {
                val keyType = privateKey?.javaClass?.simpleName ?: "null"
                throw UnsupportedKeyTypeException(alias, keyType)
            }
            
            // Extract the private key value as raw bytes
            val privateKeyValue = privateKey.s
            val privateKeyBytes = privateKeyValue.toByteArray()
            
            logd(TAG, "Extracted private key with size: ${privateKeyBytes.size} bytes")
            
            // Ensure the key is 32 bytes (normalize different formats)
            val normalizedBytes = when {
                privateKeyBytes.size == 32 -> {
                    logd(TAG, "Private key size is correct (32 bytes)")
                    privateKeyBytes
                }
                privateKeyBytes.size == 33 && privateKeyBytes[0] == 0.toByte() -> {
                    logd(TAG, "Removing leading zero byte from 33-byte key")
                    privateKeyBytes.copyOfRange(1, 33)
                }
                privateKeyBytes.size < 32 -> {
                    logd(TAG, "Padding ${privateKeyBytes.size}-byte key to 32 bytes")
                    val padded = ByteArray(32)
                    System.arraycopy(privateKeyBytes, 0, padded, 32 - privateKeyBytes.size, privateKeyBytes.size)
                    padded
                }
                else -> {
                    // This is the critical improvement - throw specific exception instead of returning null
                    loge(TAG, "Private key has unexpected size: ${privateKeyBytes.size} bytes (expected: 32)")
                    loge(TAG, "Key bytes (first 64 chars): ${privateKeyBytes.take(32).joinToString("") { "%02x".format(it) }}")
                    throw InvalidPrivateKeySizeException(
                        alias = alias,
                        actualSize = privateKeyBytes.size,
                        keyBytes = privateKeyBytes
                    )
                }
            }
            
            logd(TAG, "Successfully extracted and normalized private key from Android Keystore")
            return normalizedBytes
            
        } catch (e: KeyStoreMigrationException) {
            // Re-throw our specific exceptions
            loge(TAG, "Key extraction failed: $e")
            throw e
        } catch (e: Exception) {
            // Wrap other exceptions
            loge(TAG, "Unexpected error extracting private key: ${e.message}")
            throw KeystoreAccessException(alias, e)
        }
    }
    
    /**
     * Migrates a private key to the new storage system
     * @throws KeyStoreMigrationException with specific details about the failure
     */
    private suspend fun migratePrivateKey(
        privateKeyData: ByteArray, 
        keyId: String, 
        password: String, 
        storage: StorageProtocol
    ) {
        try {
            logd(TAG, "Migrating private key to new storage with ID: $keyId")
            logd(TAG, "Private key data size: ${privateKeyData.size} bytes")
            
            // Create a new PrivateKey instance using Flow-Wallet-Kit
            val privateKey = PrivateKey.create(storage)
            
            // Import the raw private key data
            privateKey.importPrivateKey(privateKeyData, KeyFormat.RAW)
            logd(TAG, "Successfully imported private key data")
            
            // Store the key with the new ID pattern
            privateKey.store(keyId, password)
            logd(TAG, "Successfully stored private key with new ID")
            
            // Verify the key was stored correctly by attempting to retrieve it
            val retrievedKey = PrivateKey.get(keyId, password, storage)
            logd(TAG, "Successfully retrieved stored private key for verification")
            
            // Verify the key works by generating a public key
            val publicKey = retrievedKey.publicKey(SigningAlgorithm.ECDSA_P256)
            if (publicKey == null) {
                throw KeyMigrationVerificationException(keyId, password)
            }
            
            logd(TAG, "Successfully migrated and verified private key")
            logd(TAG, "Generated public key size: ${publicKey.size} bytes")
            
        } catch (e: KeyStoreMigrationException) {
            // Re-throw our specific exceptions
            loge(TAG, "Key migration failed: $e")
            throw e
        } catch (e: Exception) {
            // Wrap other exceptions
            loge(TAG, "Unexpected error during key migration: ${e.message}")
            throw KeyMigrationStorageException(keyId, password, e)
        }
    }
    
    /**
     * Checks if migration has already been completed
     */
    private fun isMigrationCompleted(): Boolean {
        return try {
            val storage = getStorage()
            storage.get(MIGRATION_COMPLETED_KEY) != null
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Public method to check migration status (for diagnostics)
     */
    fun getMigrationStatus(): Boolean {
        return try {
            val storage = getStorage()
            storage.get(MIGRATION_COMPLETED_KEY) != null
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Marks migration as completed
     */
    private fun markMigrationCompleted() {
        try {
            val storage = getStorage()
            storage.set(MIGRATION_COMPLETED_KEY, "completed".toByteArray())
            logd(TAG, "Marked migration as completed")
        } catch (e: Exception) {
            logd(TAG, "Error marking migration as completed: ${e.message}")
        }
    }
    
    /**
     * Forces re-migration (for testing purposes only)
     */
    fun resetMigrationStatus() {
        try {
            val storage = getStorage()
            storage.remove(MIGRATION_COMPLETED_KEY)
            logd(TAG, "Reset migration status")
        } catch (e: Exception) {
            logd(TAG, "Error resetting migration status: ${e.message}")
        }
    }
    
    /**
     * Diagnostic function to list all keys in Android Keystore
     */
    fun diagnoseAndroidKeystore(): List<String> {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            
            val aliases = mutableListOf<String>()
            val enumeration = keyStore.aliases()
            while (enumeration.hasMoreElements()) {
                val alias = enumeration.nextElement()
                aliases.add(alias)
                logd(TAG, "Found alias in Android Keystore: $alias")
            }
            
            logd(TAG, "Total aliases in Android Keystore: ${aliases.size}")
            aliases
        } catch (e: Exception) {
            logd(TAG, "Error diagnosing Android Keystore: ${e.message}")
            emptyList()
        }
    }
} 