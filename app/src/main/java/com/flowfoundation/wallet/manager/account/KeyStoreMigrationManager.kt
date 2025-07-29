package com.flowfoundation.wallet.manager.account

import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.keys.KeyFormat
import com.flow.wallet.storage.StorageProtocol
import com.flowfoundation.wallet.cache.AccountCacheManager
import com.flowfoundation.wallet.utils.Env.getStorage
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
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
 *
 */
object KeyStoreMigrationManager {
    private const val TAG = "KeyStoreMigration"
    private const val OLD_KEYSTORE_ALIAS_PREFIX = "user_keystore_"
    private const val MIGRATION_COMPLETED_KEY = "keystore_migration_completed"
    private const val MIGRATION_RETRY_COUNT_KEY = "keystore_migration_retry_count"
    private const val MIGRATION_LAST_ATTEMPT_KEY = "keystore_migration_last_attempt"
    private const val MAX_RETRY_ATTEMPTS = 3
    private const val RETRY_DELAY_HOURS = 24
    private const val MAX_CONCURRENT_MIGRATIONS = 5

    /**
     * Result of a migration operation
     */
    abstract class MigrationResult {
        object AlreadyCompleted : MigrationResult()
        object SkippedDueToRetryLimit : MigrationResult()
        data class Success(val migratedCount: Int, val totalAccounts: Int, val warnings: List<String> = emptyList()) : MigrationResult()
        data class PartialSuccess(val migratedCount: Int, val totalAccounts: Int, val failures: List<MigrationFailure>) : MigrationResult()
        data class Failed(val reason: String, val failures: List<MigrationFailure> = emptyList()) : MigrationResult()
    }

    /**
     * Details about a failed migration
     */
    data class MigrationFailure(
        val prefix: String,
        val error: KeyStoreMigrationException,
        val canRetry: Boolean
    )

    /**
     * Tracks migration progress
     */
    private data class MigrationState(
        var totalAccountsProcessed: Int = 0,
        var successfulMigrations: Int = 0,
        var accountsNeedingMigration: Int = 0,
        var accountsSkipped: Int = 0
    )
    
    /**
     * Result of migrating a single account
     */
    private abstract class AccountMigrationResult {
        data class Success(val wasAlreadyMigrated: Boolean) : AccountMigrationResult()
        object NotNeeded : AccountMigrationResult()
        data class Failed(val error: KeyStoreMigrationException, val canRetry: Boolean) : AccountMigrationResult()
    }

    /**
     * Performs migration of private keys from old Android Keystore to new storage system.
     * This should be called during app startup before any wallet operations.
     */
    suspend fun performMigrationIfNeeded(): MigrationResult {
        logd(TAG, "=== Starting KeyStore Migration Check ===")

        try {
            // Check if migration has already been completed
            if (isMigrationCompleted()) {
                logd(TAG, "Migration already completed, skipping.")
                return MigrationResult.AlreadyCompleted
            }

            // Check retry conditions
            if (!shouldAttemptMigration()) {
                logd(TAG, "Migration retry conditions not met, skipping.")
                return MigrationResult.SkippedDueToRetryLimit
            }

            // Record migration attempt
            recordMigrationAttempt()

            // Get all accounts that might need migration
            val accounts = AccountCacheManager.read() ?: emptyList()
            logd(TAG, "Found ${accounts.size} accounts to check for migration")

            val storage = getStorage()
            val migrationState = MigrationState()
            val failedAccounts = mutableListOf<MigrationFailure>()

            // Process accounts with improved error handling and retry logic
            val accountsToProcess = accounts.filter { !it.prefix.isNullOrBlank() }
            logd(TAG, "Processing ${accountsToProcess.size} accounts with valid prefixes")
            
            for (account in accountsToProcess) {
                val prefix = account.prefix!!
                migrationState.totalAccountsProcessed++
                
                try {
                    val migrationResult = migrateAccountWithRetry(prefix, storage, migrationState)
                    when (migrationResult) {
                        is AccountMigrationResult.Success -> {
                            migrationState.successfulMigrations++
                            if (migrationResult.wasAlreadyMigrated) {
                                logd(TAG, "Account $prefix already migrated")
                            } else {
                                logd(TAG, "Successfully migrated account $prefix")
                                migrationState.accountsNeedingMigration++
                            }
                        }
                        is AccountMigrationResult.NotNeeded -> {
                            migrationState.successfulMigrations++
                            migrationState.accountsSkipped++
                            logd(TAG, "Account $prefix does not need migration")
                        }
                        is AccountMigrationResult.Failed -> {
                            val failure = MigrationFailure(
                                prefix = prefix,
                                error = migrationResult.error,
                                canRetry = migrationResult.canRetry
                            )
                            failedAccounts.add(failure)
                            migrationState.accountsNeedingMigration++
                            loge(TAG, "Failed to migrate account $prefix: ${migrationResult.error.message}")
                        }
                    }
                } catch (e: Exception) {
                    // Unexpected error - wrap in generic migration exception
                    val failure = MigrationFailure(
                        prefix = prefix,
                        error = KeystoreAccessException(prefix, e),
                        canRetry = true
                    )
                    failedAccounts.add(failure)
                    migrationState.accountsNeedingMigration++
                    loge(TAG, "Unexpected error processing account $prefix: ${e.message}")
                }
            }

            // Determine final result and mark completion if appropriate
            val result = determineAndMarkMigrationResult(accounts, migrationState, failedAccounts)
            logd(TAG, "Migration completed with result: $result")
            
            return result

        } catch (e: Exception) {
            loge(TAG, "Critical error during migration: ${e.message}")
            return MigrationResult.Failed("Critical migration error: ${e.message}")
        }
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
                ?: throw KeyMigrationVerificationException(keyId, password)

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
    
    /**
     * Checks if we should attempt migration based on retry logic
     */
    private fun shouldAttemptMigration(): Boolean {
        return try {
            val storage = getStorage()
            val retryCountBytes = storage.get(MIGRATION_RETRY_COUNT_KEY)
            val retryCount = retryCountBytes?.let { String(it).toIntOrNull() } ?: 0
            
            if (retryCount >= MAX_RETRY_ATTEMPTS) {
                val lastAttemptBytes = storage.get(MIGRATION_LAST_ATTEMPT_KEY)
                val lastAttempt = lastAttemptBytes?.let { String(it).toLongOrNull() } ?: 0
                val hoursSinceLastAttempt = (System.currentTimeMillis() - lastAttempt) / (1000 * 60 * 60)
                
                if (hoursSinceLastAttempt < RETRY_DELAY_HOURS) {
                    logd(TAG, "Migration retry limit reached ($retryCount/$MAX_RETRY_ATTEMPTS), waiting $RETRY_DELAY_HOURS hours")
                    return false
                } else {
                    logd(TAG, "Retry delay period passed, resetting retry count")
                    storage.set(MIGRATION_RETRY_COUNT_KEY, "0".toByteArray())
                }
            }
            
            true
        } catch (e: Exception) {
            loge(TAG, "Error checking retry conditions: ${e.message}")
            true // Default to allowing attempt
        }
    }
    
    /**
     * Records a migration attempt for retry tracking
     */
    private fun recordMigrationAttempt() {
        try {
            val storage = getStorage()
            val retryCountBytes = storage.get(MIGRATION_RETRY_COUNT_KEY)
            val retryCount = retryCountBytes?.let { String(it).toIntOrNull() } ?: 0
            
            storage.set(MIGRATION_RETRY_COUNT_KEY, (retryCount + 1).toString().toByteArray())
            storage.set(MIGRATION_LAST_ATTEMPT_KEY, System.currentTimeMillis().toString().toByteArray())
            
            logd(TAG, "Recorded migration attempt #${retryCount + 1}")
        } catch (e: Exception) {
            loge(TAG, "Error recording migration attempt: ${e.message}")
        }
    }
    
    /**
     * Migrates a single account with retry logic and improved error handling
     */
    private suspend fun migrateAccountWithRetry(
        prefix: String, 
        storage: StorageProtocol, 
        migrationState: MigrationState
    ): AccountMigrationResult {
        logd(TAG, "Processing account with prefix: $prefix")
        
        // Check if the account already has a key in the new storage system
        val newKeyId = "prefix_key_$prefix"
        if (hasKeyInNewStorage(newKeyId, prefix, storage)) {
            logd(TAG, "Account $prefix already has key in new storage")
            return AccountMigrationResult.Success(wasAlreadyMigrated = true)
        }
        
        // Attempt to migrate the key from old Android Keystore
        val oldAlias = OLD_KEYSTORE_ALIAS_PREFIX + prefix
        
        return try {
            logd(TAG, "Attempting to extract key from old keystore for prefix: $prefix")
            val privateKeyData = extractPrivateKeyFromAndroidKeystore(oldAlias)
            
            logd(TAG, "Found private key in old keystore for prefix: $prefix")
            
            // Migrate the key to the new storage system
            migratePrivateKey(privateKeyData, newKeyId, prefix, storage)
            logd(TAG, "Successfully migrated key for prefix: $prefix")
            
            AccountMigrationResult.Success(wasAlreadyMigrated = false)
            
        } catch (e: KeystoreKeyNotFoundException) {
            logd(TAG, "No key found in old keystore for prefix: $prefix (not an error - may be new account)")
            AccountMigrationResult.NotNeeded
            
        } catch (e: InvalidPrivateKeySizeException) {
            loge(TAG, "CRITICAL: Invalid private key size for prefix $prefix: ${e.getSafeDebugInfo()}")
            AccountMigrationResult.Failed(e, canRetry = false) // Size issues usually can't be retried
            
        } catch (e: KeyStoreMigrationException) {
            loge(TAG, "Migration failed for prefix $prefix: $e")
            val canRetry = when (e) {
                is KeystoreAccessException -> true // Network/storage issues can be retried
                is KeyMigrationStorageException -> true // Storage issues can be retried
                is KeyMigrationVerificationException -> false // Verification failures are persistent
                else -> true // Default to retryable
            }
            AccountMigrationResult.Failed(e, canRetry = canRetry)
            
        } catch (e: Exception) {
            loge(TAG, "Unexpected error migrating key for prefix $prefix: ${e.message}")
            AccountMigrationResult.Failed(KeystoreAccessException(oldAlias, e), canRetry = true)
        }
    }
    
    /**
     * Determines the final migration result and marks completion if appropriate
     */
    private fun determineAndMarkMigrationResult(
        accounts: List<Account>,
        migrationState: MigrationState,
        failedAccounts: List<MigrationFailure>
    ): MigrationResult {
        logd(TAG, "=== Migration Summary ===")
        logd(TAG, "- Total accounts processed: ${migrationState.totalAccountsProcessed}")
        logd(TAG, "- Successful migrations: ${migrationState.successfulMigrations}")
        logd(TAG, "- Accounts needing migration: ${migrationState.accountsNeedingMigration}")
        logd(TAG, "- Accounts skipped: ${migrationState.accountsSkipped}")
        logd(TAG, "- Failed accounts: ${failedAccounts.size}")
        
        val warnings = mutableListOf<String>()
        
        // Determine if migration should be marked as completed
        val shouldMarkCompleted = when {
            // Case 1: No accounts exist - nothing to migrate
            accounts.isEmpty() -> {
                logd(TAG, "No accounts found - marking migration as completed")
                true
            }
            
            // Case 2: No accounts have prefix (all keystore-based) - no migration needed
            migrationState.totalAccountsProcessed == 0 -> {
                logd(TAG, "No prefix-based accounts found - marking migration as completed")
                warnings.add("No accounts with prefix found")
                true
            }
            
            // Case 3: All accounts were successfully processed
            failedAccounts.isEmpty() -> {
                logd(TAG, "All accounts successfully processed - marking migration as completed")
                true
            }
            
            // Case 4: Some accounts failed but none can be retried
            failedAccounts.isNotEmpty() && failedAccounts.none { it.canRetry } -> {
                logd(TAG, "Some accounts failed but none can be retried - marking as completed")
                warnings.add("${failedAccounts.size} accounts failed migration with non-retryable errors")
                true
            }
            
            // Case 5: Some accounts failed and can be retried - DO NOT mark as completed
            failedAccounts.any { it.canRetry } -> {
                loge(TAG, "Some accounts failed migration with retryable errors - NOT marking as completed")
                loge(TAG, "Failed accounts: ${failedAccounts.size}")
                loge(TAG, "Migration will be retried on next app launch")
                false
            }
            
            else -> false
        }
        
        return if (shouldMarkCompleted) {
            markMigrationCompleted()
            logd(TAG, "Migration process marked as completed")
            
            if (failedAccounts.isEmpty()) {
                MigrationResult.Success(
                    migratedCount = migrationState.accountsNeedingMigration,
                    totalAccounts = migrationState.totalAccountsProcessed,
                    warnings = warnings
                )
            } else {
                MigrationResult.PartialSuccess(
                    migratedCount = migrationState.successfulMigrations,
                    totalAccounts = migrationState.totalAccountsProcessed,
                    failures = failedAccounts
                )
            }
        } else {
            logd(TAG, "Migration process completed with retryable failures")
            MigrationResult.PartialSuccess(
                migratedCount = migrationState.successfulMigrations,
                totalAccounts = migrationState.totalAccountsProcessed,
                failures = failedAccounts
            )
        }
    }
}
