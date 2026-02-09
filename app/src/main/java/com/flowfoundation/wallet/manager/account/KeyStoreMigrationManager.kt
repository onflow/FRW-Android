package com.flowfoundation.wallet.manager.account

import com.flowfoundation.wallet.utils.logd
import java.security.KeyStore

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
