package com.flowfoundation.wallet.manager.account

/**
 * Base exception for KeyStore migration failures.
 * Provides detailed context for debugging migration issues.
 * 
 * Note: Using abstract class instead of sealed class to avoid ASM9 compatibility issues.
 */
abstract class KeyStoreMigrationException(
    message: String,
    cause: Throwable? = null,
    val prefix: String? = null,
    val alias: String? = null
) : Exception(message, cause) {
    
    override fun toString(): String {
        return buildString {
            append(this@KeyStoreMigrationException::class.simpleName)
            append(": ")
            append(message)
            prefix?.let { append(" [prefix: $it]") }
            alias?.let { append(" [alias: $it]") }
            cause?.let { append(" [cause: ${it.message}]") }
        }
    }
}

/**
 * Thrown when a key cannot be found in the Android Keystore
 */
class KeystoreKeyNotFoundException(
    alias: String,
    prefix: String? = null
) : KeyStoreMigrationException(
    message = "Private key not found in Android Keystore",
    prefix = prefix,
    alias = alias
)

/**
 * Thrown when the keystore entry exists but is not a private key entry
 */
class InvalidKeystoreEntryException(
    alias: String,
    entryType: String,
    prefix: String? = null
) : KeyStoreMigrationException(
    message = "Keystore entry is not a PrivateKeyEntry (found: $entryType)",
    prefix = prefix,
    alias = alias
)

/**
 * Thrown when the private key is not an EC key
 */
class UnsupportedKeyTypeException(
    alias: String,
    keyType: String,
    prefix: String? = null
) : KeyStoreMigrationException(
    message = "Private key is not an EC key (found: $keyType)",
    prefix = prefix,
    alias = alias
)

/**
 * Thrown when the private key size is unexpected and cannot be normalized
 */
class InvalidPrivateKeySizeException(
    alias: String,
    actualSize: Int,
    val expectedSize: Int = 32,
    prefix: String? = null,
    val keyBytes: ByteArray? = null
) : KeyStoreMigrationException(
    message = "Private key has unexpected size: $actualSize bytes (expected: $expectedSize bytes). This could indicate key corruption or unsupported key format.",
    prefix = prefix,
    alias = alias
) {
    
    /**
     * ⚠️ SECURITY WARNING: This method exposes raw private key material!
     * 
     * This should ONLY be used for debugging in development builds.
     * NEVER call this method in production code or logs, as it could
     * expose users' private keys and compromise their funds.
     * 
     * @return Hex-encoded private key bytes - HIGHLY SENSITIVE MATERIAL!
     */
    fun getKeyBytesHex(): String? {
        return keyBytes?.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Safe method to get debugging information without exposing key material.
     * Use this method in production logging instead of getKeyBytesHex().
     * 
     * @return Non-sensitive debugging information
     */
    fun getSafeDebugInfo(): String {
        return "actualSize=${keyBytes?.size}, expectedSize=$expectedSize, alias=$alias"
    }
}

/**
 * Thrown when private key extraction fails due to Android Keystore access issues
 */
class KeystoreAccessException(
    alias: String,
    cause: Throwable,
    prefix: String? = null
) : KeyStoreMigrationException(
    message = "Failed to access Android Keystore",
    cause = cause,
    prefix = prefix,
    alias = alias
)

/**
 * Thrown when the migrated key fails verification
 */
class KeyMigrationVerificationException(
    keyId: String,
    prefix: String? = null,
    cause: Throwable? = null
) : KeyStoreMigrationException(
    message = "Migrated key failed verification - could not generate public key",
    cause = cause,
    prefix = prefix,
    alias = keyId
)

/**
 * Thrown when storing the migrated key fails
 */
class KeyMigrationStorageException(
    keyId: String,
    prefix: String? = null,
    cause: Throwable? = null
) : KeyStoreMigrationException(
    message = "Failed to store migrated key in new storage system",
    cause = cause,
    prefix = prefix,
    alias = keyId
) 