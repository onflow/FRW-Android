package com.flowfoundation.wallet.manager.account

import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.keys.KeyFormat
import com.flow.wallet.storage.StorageProtocol
import com.flowfoundation.wallet.cache.AccountCacheManager
import com.flowfoundation.wallet.utils.Env.getStorage
import com.flowfoundation.wallet.utils.logd
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
                    
                    // Check if the account has a key in the old Android Keystore
                    val oldAlias = OLD_KEYSTORE_ALIAS_PREFIX + prefix
                    val privateKeyData = extractPrivateKeyFromAndroidKeystore(oldAlias)
                    
                    if (privateKeyData != null) {
                        logd(TAG, "Found private key in old keystore for prefix: $prefix")
                        
                        // Migrate the key to the new storage system
                        if (migratePrivateKey(privateKeyData, newKeyId, prefix, storage)) {
                            logd(TAG, "Successfully migrated key for prefix: $prefix")
                            migrationPerformed = true
                        } else {
                            logd(TAG, "Failed to migrate key for prefix: $prefix")
                        }
                    } else {
                        logd(TAG, "No key found in old keystore for prefix: $prefix")
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
     */
    private fun extractPrivateKeyFromAndroidKeystore(alias: String): ByteArray? {
        return try {
            logd(TAG, "Attempting to extract key from Android Keystore with alias: $alias")
            
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            
            if (!keyStore.containsAlias(alias)) {
                logd(TAG, "Alias $alias not found in Android Keystore")
                return null
            }
            
            val keyEntry = keyStore.getEntry(alias, null) as? PrivateKeyEntry
            if (keyEntry == null) {
                logw(TAG, "Key entry not found for alias: $alias")
                return null
            }
            
            val privateKey = keyEntry.privateKey as? ECPrivateKey
            if (privateKey == null) {
                logw(TAG, "Private key is not an EC key for alias: $alias")
                return null
            }
            
            // Extract the private key value as raw bytes
            val privateKeyValue = privateKey.s
            val privateKeyBytes = privateKeyValue.toByteArray()
            
            // Ensure the key is 32 bytes (pad with zeros if necessary)
            val normalizedBytes = when {
                privateKeyBytes.size == 32 -> privateKeyBytes
                privateKeyBytes.size == 33 && privateKeyBytes[0] == 0.toByte() -> 
                    privateKeyBytes.copyOfRange(1, 33) // Remove leading zero byte
                privateKeyBytes.size < 32 -> {
                    val padded = ByteArray(32)
                    System.arraycopy(privateKeyBytes, 0, padded, 32 - privateKeyBytes.size, privateKeyBytes.size)
                    padded
                }
                else -> {
                    logw(TAG, "Unexpected private key size: ${privateKeyBytes.size}")
                    return null
                }
            }
            
            logd(TAG, "Successfully extracted private key from Android Keystore")
            normalizedBytes
            
        } catch (e: Exception) {
            logd(TAG, "Error extracting private key from Android Keystore: ${e.message}")
            null
        }
    }
    
    /**
     * Migrates a private key to the new storage system
     */
    private suspend fun migratePrivateKey(
        privateKeyData: ByteArray, 
        keyId: String, 
        password: String, 
        storage: StorageProtocol
    ): Boolean {
        return try {
            logd(TAG, "Migrating private key to new storage with ID: $keyId")
            
            // Create a new PrivateKey instance using Flow-Wallet-Kit
            val privateKey = PrivateKey.create(storage)
            
            // Import the raw private key data
            privateKey.importPrivateKey(privateKeyData, KeyFormat.RAW)
            
            // Store the key with the new ID pattern
            privateKey.store(keyId, password)
            
            // Verify the key was stored correctly by attempting to retrieve it
            val retrievedKey = PrivateKey.get(keyId, password, storage)
            
            // Verify the key works by generating a public key
            val publicKey = retrievedKey.publicKey(SigningAlgorithm.ECDSA_P256)
            if (publicKey == null) {
                logd(TAG, "Failed to generate public key from migrated private key")
                return false
            }
            
            logd(TAG, "Successfully migrated and verified private key")
            true
            
        } catch (e: Exception) {
            logd(TAG, "Error migrating private key: ${e.message}")
            false
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