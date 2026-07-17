package com.flowfoundation.wallet.manager.account

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flow.wallet.KeyManager
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyStoreMigrationManagerAndroidTest {

    @Test
    fun realAndroidKeystoreCertificateParticipatesInDiscovery() = runBlocking {
        val aliasPrefix = KeyManager.KEYSTORE_ALIAS_PREFIX + "recovery_test_"
        val alias = aliasPrefix + "alpha"
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.deleteEntry(alias)

        try {
            val pair = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                "AndroidKeyStore",
            ).apply {
                initialize(
                    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .build()
                )
            }.generateKeyPair()
            val expected = KeyStoreMigrationManager.flowPublicKey(pair.public as ECPublicKey)!!

            val discovery = KeyStoreMigrationManager.discoverOrphanedKeystoreKeys(
                knownAddresses = setOf("0x01"),
                aliasPrefix = aliasPrefix,
                keystore = KeyStoreMigrationManager.androidKeystoreSource(),
                onChainKeys = KeyStoreMigrationManager.OnChainKeySource {
                    listOf(
                        KeyStoreMigrationManager.OnChainKey(expected, revoked = false)
                    )
                },
            )

            assertTrue(discovery.isComplete)
            assertEquals(
                listOf(KeyStoreMigrationManager.RecoveryMatch("alpha", "0x01", expected)),
                discovery.matches,
            )
        } finally {
            keyStore.deleteEntry(alias)
        }
    }
}
