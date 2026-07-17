package com.flowfoundation.wallet.manager.account

import java.math.BigInteger
import java.security.interfaces.ECPublicKey
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class KeyStoreMigrationManagerTest {

    @Test
    fun `normalizes Flow and SEC1 public keys without truncating Flow keys`() {
        val flowKeyBeginningWith04 = "04" + "ab".repeat(63)
        val sec1UncompressedKey = "04" + "cd".repeat(64)

        assertThat(KeyStoreMigrationManager.normalizePublicKey(flowKeyBeginningWith04))
            .isEqualTo(flowKeyBeginningWith04)
        assertThat(KeyStoreMigrationManager.normalizePublicKey(sec1UncompressedKey))
            .isEqualTo("cd".repeat(64))
    }

    @Test
    fun `filters only non-empty legacy Flow aliases`() {
        assertThat(
            KeyStoreMigrationManager.filterLegacyAliases(
                aliases = listOf(
                    "unrelated",
                    "user_keystore_",
                    "user_keystore_alpha",
                    "user_keystore_beta",
                ),
                aliasPrefix = "user_keystore_",
            )
        ).containsExactly("user_keystore_alpha", "user_keystore_beta")
    }

    @Test
    fun `pads EC coordinates to 32 bytes`() {
        val publicKey = FakeECPublicKey(BigInteger.ONE, BigInteger("80", 16))

        assertThat(KeyStoreMigrationManager.flowPublicKey(publicKey)).isEqualTo(
            "00".repeat(31) + "01" + "00".repeat(31) + "80"
        )
    }

    @Test
    fun `discovers multiple aliases and preserves one key to many addresses`() = runTest {
        val keyA = FakeECPublicKey(BigInteger.ONE, BigInteger.TWO)
        val keyB = FakeECPublicKey(BigInteger.valueOf(3), BigInteger.valueOf(4))
        val flowKeyA = KeyStoreMigrationManager.flowPublicKey(keyA)!!
        val flowKeyB = KeyStoreMigrationManager.flowPublicKey(keyB)!!
        val keystore = FakeKeystore(
            aliases = listOf("other", "legacy_alpha", "legacy_beta"),
            publicKeys = mapOf("legacy_alpha" to keyA, "legacy_beta" to keyB),
        )

        val discovery = KeyStoreMigrationManager.discoverOrphanedKeystoreKeys(
            knownAddresses = linkedSetOf("0x01", "0x02", "0x03"),
            aliasPrefix = "legacy_",
            keystore = keystore,
            onChainKeys = KeyStoreMigrationManager.OnChainKeySource { address ->
                when (address) {
                    "0x01" -> listOf(
                        KeyStoreMigrationManager.OnChainKey("04$flowKeyA", revoked = false)
                    )
                    "0x02" -> listOf(
                        KeyStoreMigrationManager.OnChainKey(flowKeyA, revoked = false),
                        KeyStoreMigrationManager.OnChainKey(flowKeyB, revoked = true),
                    )
                    else -> listOf(
                        KeyStoreMigrationManager.OnChainKey(flowKeyB, revoked = false)
                    )
                }
            },
        )

        assertThat(discovery.isComplete).isTrue()
        assertThat(discovery.matches).containsExactlyInAnyOrder(
            KeyStoreMigrationManager.RecoveryMatch("alpha", "0x01", flowKeyA),
            KeyStoreMigrationManager.RecoveryMatch("alpha", "0x02", flowKeyA),
            KeyStoreMigrationManager.RecoveryMatch("beta", "0x03", flowKeyB),
        )
    }

    @Test
    fun `partial failures are retried by the next discovery`() = runTest {
        val key = FakeECPublicKey(BigInteger.TEN, BigInteger.valueOf(11))
        val flowKey = KeyStoreMigrationManager.flowPublicKey(key)!!
        val keystore = FakeKeystore(
            aliases = listOf("legacy_alpha"),
            publicKeys = mapOf("legacy_alpha" to key),
        )
        var shouldFail = true
        val source = KeyStoreMigrationManager.OnChainKeySource {
            if (shouldFail) error("offline")
            listOf(KeyStoreMigrationManager.OnChainKey(flowKey, revoked = false))
        }

        val first = KeyStoreMigrationManager.discoverOrphanedKeystoreKeys(
            knownAddresses = setOf("0x01"),
            aliasPrefix = "legacy_",
            keystore = keystore,
            onChainKeys = source,
        )
        shouldFail = false
        val second = KeyStoreMigrationManager.discoverOrphanedKeystoreKeys(
            knownAddresses = setOf("0x01"),
            aliasPrefix = "legacy_",
            keystore = keystore,
            onChainKeys = source,
        )

        assertThat(first.isComplete).isFalse()
        assertThat(first.failures.single().message).isEqualTo("offline")
        assertThat(second.isComplete).isTrue()
        assertThat(second.matches).containsExactly(
            KeyStoreMigrationManager.RecoveryMatch("alpha", "0x01", flowKey)
        )
    }

    @Test
    fun `certificate read failure is retried by the next discovery`() = runTest {
        val key = FakeECPublicKey(BigInteger.valueOf(12), BigInteger.valueOf(13))
        val flowKey = KeyStoreMigrationManager.flowPublicKey(key)!!
        var shouldFail = true
        val keystore = object : KeyStoreMigrationManager.LegacyKeystoreSource {
            override fun aliases(): List<String> = listOf("legacy_alpha")

            override fun publicKey(alias: String): ECPublicKey {
                if (shouldFail) error("certificate unavailable")
                return key
            }
        }
        val source = KeyStoreMigrationManager.OnChainKeySource {
            listOf(KeyStoreMigrationManager.OnChainKey(flowKey, revoked = false))
        }

        val first = KeyStoreMigrationManager.discoverOrphanedKeystoreKeys(
            knownAddresses = setOf("0x01"),
            aliasPrefix = "legacy_",
            keystore = keystore,
            onChainKeys = source,
        )
        shouldFail = false
        val second = KeyStoreMigrationManager.discoverOrphanedKeystoreKeys(
            knownAddresses = setOf("0x01"),
            aliasPrefix = "legacy_",
            keystore = keystore,
            onChainKeys = source,
        )

        assertThat(first.isComplete).isFalse()
        assertThat(first.failures.single().message).isEqualTo("certificate unavailable")
        assertThat(second.isComplete).isTrue()
        assertThat(second.matches).hasSize(1)
    }

    private class FakeKeystore(
        private val aliases: List<String>,
        private val publicKeys: Map<String, ECPublicKey>,
    ) : KeyStoreMigrationManager.LegacyKeystoreSource {
        override fun aliases(): List<String> = aliases

        override fun publicKey(alias: String): ECPublicKey? = publicKeys[alias]
    }

    private class FakeECPublicKey(
        x: BigInteger,
        y: BigInteger,
    ) : ECPublicKey {
        private val point = ECPoint(x, y)

        override fun getW(): ECPoint = point
        override fun getParams(): ECParameterSpec? = null
        override fun getAlgorithm(): String = "EC"
        override fun getFormat(): String = "X.509"
        override fun getEncoded(): ByteArray = byteArrayOf()
    }
}
