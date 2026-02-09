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
) : Exception(message, cause) {

    override fun toString(): String {
        return buildString {
            append(this@KeyStoreMigrationException::class.simpleName)
            append(": ")
            append(message)
            prefix?.let { append(" [prefix: $it]") }
            cause?.let { append(" [cause: ${it.message}]") }
        }
    }
}

/**
 * Thrown when a hardware-backed key is detected that cannot be extracted.
 * Hardware-backed keys are stored in the device's secure hardware element
 * and cannot be extracted for migration. These keys require special handling
 * via AndroidKeystoreCryptoProvider.
 */
class HardwareBackedKeyException(
    prefix: String,
    message: String = "Hardware-backed key cannot be extracted for migration",
) : KeyStoreMigrationException(
    message = message,
    prefix = prefix
)
