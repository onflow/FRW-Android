package com.flowfoundation.wallet.manager.account

import com.flow.wallet.KeyManager
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.flow.FlowCadenceApi
import com.flowfoundation.wallet.manager.walletdata.FlowWallet
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import java.security.KeyStore
import java.security.interfaces.ECPublicKey

/**
 * Discovers legacy Flow keys that still exist in Android Keystore but are no longer mapped to
 * their locally known accounts.
 *
 * Recovery is deliberately limited to Flow addresses already discoverable from local account
 * state. This scanner only returns evidence; [AccountManager] owns all account mutations,
 * persistence, and UI publication.
 */
object KeyStoreMigrationManager {
    private const val TAG = "KeyStoreMigration"

    data class RecoveryMatch(
        val prefix: String,
        val address: String,
        val publicKey: String,
    )

    data class RecoveryFailure(
        val target: String,
        val message: String,
    )

    data class RecoveryDiscovery(
        val matches: List<RecoveryMatch>,
        val failures: List<RecoveryFailure>,
        val isComplete: Boolean,
    )

    internal data class OnChainKey(
        val publicKey: String,
        val revoked: Boolean,
    )

    internal fun interface OnChainKeySource {
        suspend fun keys(address: String): List<OnChainKey>
    }

    internal interface LegacyKeystoreSource {
        fun aliases(): List<String>
        fun publicKey(alias: String): ECPublicKey?
    }

    /**
     * Diagnostic function to list all keys in Android Keystore.
     */
    fun diagnoseAndroidKeystore(): List<String> {
        return try {
            AndroidLegacyKeystoreSource().aliases().onEach { alias ->
                logd(TAG, "Found alias in Android Keystore: $alias")
            }
        } catch (e: Exception) {
            loge(TAG, "Error diagnosing Android Keystore: ${e.message}")
            emptyList()
        }
    }

    /**
     * Discovers recoverable legacy aliases for locally known addresses without mutating caches.
     * Every invocation performs a fresh scan so partial failures never suppress a later retry.
     */
    suspend fun discoverOrphanedKeystoreKeys(
        knownAddresses: Set<String> = collectKnownAddresses(),
    ): RecoveryDiscovery {
        val keystore = try {
            androidKeystoreSource()
        } catch (e: Exception) {
            return RecoveryDiscovery(
                matches = emptyList(),
                failures = listOf(
                    RecoveryFailure("AndroidKeyStore", e.message ?: e.javaClass.simpleName)
                ),
                isComplete = false,
            )
        }

        return discoverOrphanedKeystoreKeys(
            knownAddresses = knownAddresses,
            aliasPrefix = KeyManager.KEYSTORE_ALIAS_PREFIX,
            keystore = keystore,
            onChainKeys = OnChainKeySource { address ->
                FlowCadenceApi.getAccount(address).keys.orEmpty().map { key ->
                    OnChainKey(publicKey = key.publicKey, revoked = key.revoked)
                }
            },
        )
    }

    internal suspend fun discoverOrphanedKeystoreKeys(
        knownAddresses: Set<String>,
        aliasPrefix: String,
        keystore: LegacyKeystoreSource,
        onChainKeys: OnChainKeySource,
    ): RecoveryDiscovery {
        val failures = mutableListOf<RecoveryFailure>()
        val aliases = try {
            filterLegacyAliases(keystore.aliases(), aliasPrefix)
        } catch (e: Exception) {
            return RecoveryDiscovery(
                matches = emptyList(),
                failures = listOf(
                    RecoveryFailure("AndroidKeyStore", e.message ?: e.javaClass.simpleName)
                ),
                isComplete = false,
            )
        }

        val addressesByPublicKey = mutableMapOf<String, MutableSet<String>>()
        knownAddresses.distinctBy(::normalizeAddress).forEach { address ->
            try {
                onChainKeys.keys(address)
                    .asSequence()
                    .filterNot { it.revoked }
                    .forEach { key ->
                        val normalized = normalizePublicKey(key.publicKey)
                        if (normalized == null) {
                            failures += RecoveryFailure(
                                target = address,
                                message = "Invalid on-chain public key",
                            )
                        } else {
                            addressesByPublicKey.getOrPut(normalized) { linkedSetOf() }
                                .add(address)
                        }
                    }
            } catch (e: Exception) {
                failures += RecoveryFailure(address, e.message ?: e.javaClass.simpleName)
            }
        }

        val matches = mutableListOf<RecoveryMatch>()
        aliases.forEach { alias ->
            val publicKey = try {
                keystore.publicKey(alias)?.let(::flowPublicKey)
            } catch (e: Exception) {
                failures += RecoveryFailure(alias, e.message ?: e.javaClass.simpleName)
                null
            }

            if (publicKey == null) {
                if (failures.none { it.target == alias }) {
                    failures += RecoveryFailure(alias, "Missing or invalid EC certificate public key")
                }
                return@forEach
            }

            addressesByPublicKey[publicKey].orEmpty().forEach { address ->
                matches += RecoveryMatch(
                    prefix = alias.removePrefix(aliasPrefix),
                    address = address,
                    publicKey = publicKey,
                )
            }
        }

        return RecoveryDiscovery(
            matches = matches.distinctBy { Triple(it.prefix, normalizeAddress(it.address), it.publicKey) },
            failures = failures,
            isComplete = failures.isEmpty(),
        )
    }

    internal fun filterLegacyAliases(aliases: List<String>, aliasPrefix: String): List<String> {
        return aliases.filter { alias ->
            alias.startsWith(aliasPrefix) && alias.length > aliasPrefix.length
        }
    }

    internal fun androidKeystoreSource(): LegacyKeystoreSource {
        return AndroidLegacyKeystoreSource()
    }

    /**
     * Normalizes Flow's 128-character x||y form and SEC1's 130-character 04||x||y form.
     * A valid 128-character Flow key that naturally starts with 04 is left intact.
     */
    internal fun normalizePublicKey(publicKey: String): String? {
        val withoutHexPrefix = publicKey.trim().let { value ->
            if (value.startsWith("0x", ignoreCase = true)) value.substring(2) else value
        }
        val normalized = if (
            withoutHexPrefix.length == 130 &&
            withoutHexPrefix.startsWith("04", ignoreCase = true)
        ) {
            withoutHexPrefix.substring(2)
        } else {
            withoutHexPrefix
        }

        return normalized.lowercase().takeIf { value ->
            value.length == 128 && value.all { it in '0'..'9' || it in 'a'..'f' }
        }
    }

    internal fun flowPublicKey(publicKey: ECPublicKey): String? {
        val x = paddedCoordinate(publicKey.w.affineX.toByteArray()) ?: return null
        val y = paddedCoordinate(publicKey.w.affineY.toByteArray()) ?: return null
        return (x + y).joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private fun paddedCoordinate(bytes: ByteArray): ByteArray? {
        val unsigned = when {
            bytes.size == 33 && bytes.first() == 0.toByte() -> bytes.copyOfRange(1, bytes.size)
            bytes.size <= 32 -> bytes
            else -> return null
        }
        return ByteArray(32).also { padded ->
            unsigned.copyInto(padded, destinationOffset = padded.size - unsigned.size)
        }
    }

    internal fun normalizeAddress(address: String): String {
        return if (address.startsWith("0x", ignoreCase = true)) {
            address.substring(2).lowercase()
        } else {
            address.lowercase()
        }
    }

    internal fun locallyKnownFlowAddresses(
        account: Account,
        network: String = chainNetWorkString(),
    ): Set<String> {
        val nodeAddresses = account.walletNodes
            .filterIsInstance<FlowWallet>()
            .filter { it.chainIdString.equals(network, ignoreCase = true) }
            .map { it.address }
        val cachedAddresses = account.wallet?.wallets.orEmpty()
            .flatMap { it.blockchain.orEmpty() }
            .filter { it.chainId.isBlank() || it.chainId.equals(network, ignoreCase = true) }
            .map { it.address }
        return (nodeAddresses + cachedAddresses).filterTo(linkedSetOf()) { it.isNotBlank() }
    }

    private fun collectKnownAddresses(): Set<String> {
        return AccountManager.list().flatMapTo(linkedSetOf()) { account ->
            locallyKnownFlowAddresses(account)
        }
    }

    private class AndroidLegacyKeystoreSource : LegacyKeystoreSource {
        private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        override fun aliases(): List<String> {
            val aliases = mutableListOf<String>()
            val entries = keyStore.aliases()
            while (entries.hasMoreElements()) {
                aliases += entries.nextElement()
            }
            return aliases
        }

        override fun publicKey(alias: String): ECPublicKey? {
            return keyStore.getCertificate(alias)?.publicKey as? ECPublicKey
        }
    }
}
