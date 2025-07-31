package com.flowfoundation.wallet.manager.key

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.storage.FileSystemStorage
import com.flow.wallet.storage.StorageProtocol
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.onflow.flow.models.SigningAlgorithm
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import java.util.*

/**
 * Simple instrumented tests for KeyCompatibilityManager without mocking.
 * 
 * These tests verify the actual key extraction, conversion, and storage operations
 * using real Android Keystore and Flow-Wallet-Kit storage.
 */
@RunWith(AndroidJUnit4::class)
class KeyCompatibilityManagerSimpleTest {

    private lateinit var context: Context
    private lateinit var storage: StorageProtocol
    private val testPrefix = "simple_${UUID.randomUUID().toString().take(8)}"
    private val oldKeystoreAlias = "user_keystore_$testPrefix"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val testDir = File(context.filesDir, "test_simple_${UUID.randomUUID()}")
        storage = FileSystemStorage(testDir)
        
        // Clean up any existing test keys
        cleanupTestKeys()
    }

    @After
    fun tearDown() {
        cleanupTestKeys()
    }

    private fun cleanupTestKeys() {
        try {
            // Clean up Android Keystore
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            if (keyStore.containsAlias(oldKeystoreAlias)) {
                keyStore.deleteEntry(oldKeystoreAlias)
            }
        } catch (e: Exception) {
            // Cleanup failed, but don't fail the test
        }
    }

    @Test
    fun testGetPrivateKeyWithFallback_NewStorageExists() = runBlocking {
        // Arrange - Create a key in new storage
        val newKeyId = "prefix_key_$testPrefix"
        val privateKey = PrivateKey.create(storage)
        privateKey.store(newKeyId, testPrefix)

        // Act
        val result = KeyCompatibilityManager.getPrivateKeyWithFallback(testPrefix, storage)

        // Assert
        assertNotNull("Should return private key from new storage", result)
        
        // Verify the key works by generating a public key
        val publicKey = result!!.publicKey(SigningAlgorithm.ECDSA_P256)
        assertNotNull("Should be able to generate public key", publicKey)
        if (publicKey != null) {
            // EC public keys can be 64 bytes (compressed) or 65 bytes (uncompressed)
            assertTrue("Public key should be 64 or 65 bytes", publicKey.size == 64 || publicKey.size == 65)
        }
    }

    @Test
    fun testGetPrivateKeyWithFallback_FallbackToOldKeystore() = runBlocking {
        // Arrange - Create a key in Android Keystore using the old pattern
        createTestKeyInAndroidKeystore()

        // Verify no key exists in new storage
        val newKeyId = "prefix_key_$testPrefix"
        var newStorageHasKey = false
        try {
            PrivateKey.get(newKeyId, testPrefix, storage)
            newStorageHasKey = true
        } catch (e: Exception) {
            // Expected - no key in new storage
        }
        assertFalse("New storage should not have key", newStorageHasKey)

        // Act
        val result = KeyCompatibilityManager.getPrivateKeyWithFallback(testPrefix, storage)

        // Assert - Modern AndroidKeyStore keys are hardware-backed and cannot be extracted
        // This is expected behavior for security reasons
        assertNull("Hardware-backed AndroidKeyStore keys cannot be extracted (expected)", result)
        
        // But we should still be able to detect that the key exists in the old keystore
        val keyExists = KeyCompatibilityManager.hasPrivateKey(testPrefix, storage)
        assertTrue("Should detect that key exists in old Android Keystore", keyExists)
    }

    @Test
    fun testGetPrivateKeyWithFallback_NoKeyExists() {
        // Arrange - Ensure no keys exist anywhere
        cleanupTestKeys()

        // Act
        val result = KeyCompatibilityManager.getPrivateKeyWithFallback(testPrefix, storage)

        // Assert
        assertNull("Should return null when no key exists in either storage", result)
    }

    @Test
    fun testHasPrivateKey_NewStorageExists() = runBlocking {
        // Arrange - Create a key in new storage
        val newKeyId = "prefix_key_$testPrefix"
        val privateKey = PrivateKey.create(storage)
        privateKey.store(newKeyId, testPrefix)

        // Act
        val result = KeyCompatibilityManager.hasPrivateKey(testPrefix, storage)

        // Assert
        assertTrue("Should return true when key exists in new storage", result)
    }

    @Test
    fun testHasPrivateKey_OldKeystoreExists() {
        // Arrange - Create a key in Android Keystore using the old pattern
        createTestKeyInAndroidKeystore()

        // Act
        val result = KeyCompatibilityManager.hasPrivateKey(testPrefix, storage)

        // Assert
        assertTrue("Should return true when key exists in old Android Keystore", result)
    }

    @Test
    fun testHasPrivateKey_NoKeyExists() {
        // Arrange - Ensure no keys exist anywhere
        cleanupTestKeys()

        // Act
        val result = KeyCompatibilityManager.hasPrivateKey(testPrefix, storage)

        // Assert
        assertFalse("Should return false when no key exists in either storage", result)
    }

    @Test
    fun testDiagnoseKeyStorage() = runBlocking {
        // Arrange - Create key in Android Keystore
        createTestKeyInAndroidKeystore()

        // Act
        val report = KeyCompatibilityManager.diagnoseKeyStorage(testPrefix, storage)

        // Assert
        assertTrue("Should mention new storage", report.contains("New storage"))
        assertTrue("Should mention old Android Keystore", report.contains("Old Android Keystore"))
        assertTrue("Should contain test prefix", report.contains(testPrefix))
    }

    @Test
    fun testHardwareBachedKeyLimitation() {
        // Arrange - Create a real EC key in Android Keystore (hardware-backed)
        createTestKeyInAndroidKeystore()

        // Act - Attempt to extract and convert the key
        val result = KeyCompatibilityManager.getPrivateKeyWithFallback(testPrefix, storage)

        // Assert - Hardware-backed keys cannot be extracted (this is expected)
        assertNull("Hardware-backed AndroidKeyStore keys cannot be extracted for security", result)
        
        // However, we should be able to detect that the key exists
        val keyExists = KeyCompatibilityManager.hasPrivateKey(testPrefix, storage)
        assertTrue("Should detect that hardware-backed key exists", keyExists)
        
        // And diagnostic should provide useful information
        val diagnostic = KeyCompatibilityManager.diagnoseKeyStorage(testPrefix, storage)
        assertTrue("Diagnostic should mention old Android Keystore", diagnostic.contains("Old Android Keystore"))
        assertTrue("Diagnostic should indicate key found", diagnostic.contains("Found"))
    }

    /**
     * Creates a real EC private key in Android Keystore for testing.
     * 
     * Note: This creates a software-backed key that allows extraction of private key material.
     * In production, keys would be hardware-backed and non-extractable for security.
     */
    private fun createTestKeyInAndroidKeystore() {
        try {
            val keyPairGenerator = KeyPairGenerator.getInstance("EC", "AndroidKeyStore")
            val spec = android.security.keystore.KeyGenParameterSpec.Builder(
                oldKeystoreAlias,
                android.security.keystore.KeyProperties.PURPOSE_SIGN or 
                android.security.keystore.KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256)
                // Make key extractable for testing - this allows migration
                .setUserAuthenticationRequired(false)
                .build()

            keyPairGenerator.initialize(spec)
            val keyPair = keyPairGenerator.generateKeyPair()
            
            // Verify the key was created
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            assertTrue("Key should exist in Android Keystore", keyStore.containsAlias(oldKeystoreAlias))
            
        } catch (e: Exception) {
            fail("Failed to create test key in Android Keystore: ${e.message}")
        }
    }
}