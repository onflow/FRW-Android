package io.outblock.wallet

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStore.PrivateKeyEntry
import java.security.PrivateKey
import java.security.spec.ECGenParameterSpec
import java.util.Enumeration

object KeyManager {
    private const val KEYSTORE_ALIAS_PREFIX = "user_keystore_"
    private const val TEMP_ALIAS = "temp_alias"
    private const val KEYSTORE_FILENAME = "keystore.jks"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore")

    init {
        keyStore.load(null)
    }

    fun generateKey(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )

        val keyGenSpec = KeyGenParameterSpec.Builder(
            TEMP_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setAlgorithmParameterSpec(ECGenParameterSpec("P-256"))
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .build()

        keyPairGenerator.initialize(keyGenSpec)
        return keyPairGenerator.generateKeyPair()
    }

    fun generateKeyWithPrefix(prefix: String): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )

        val keyGenSpec = KeyGenParameterSpec.Builder(
            generateAlias(prefix), KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setAlgorithmParameterSpec(ECGenParameterSpec("P-256"))
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .build()

        keyPairGenerator.initialize(keyGenSpec)
        return keyPairGenerator.generateKeyPair()
    }

    fun generateKeyEntry(): PrivateKeyEntry? {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )

        val keyGenSpec = KeyGenParameterSpec.Builder(
            TEMP_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setAlgorithmParameterSpec(ECGenParameterSpec("P-256"))
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .build()

        keyPairGenerator.initialize(keyGenSpec)
        keyPairGenerator.generateKeyPair()
        val entry = keyStore.getEntry(TEMP_ALIAS, null)
        return entry as? PrivateKeyEntry
    }

    fun storeKeyPairWithPrefix(prefix: String, keyPair: KeyPair) {
        val entry = PrivateKeyEntry(keyPair.private, null)
        keyStore.setEntry(generateAlias(prefix), entry, null)
        keyStore.deleteEntry(TEMP_ALIAS)
    }

    fun storeKeyEntryWithPrefix(prefix: String, keyEntry: PrivateKeyEntry) {
        val newEntry = PrivateKeyEntry(keyEntry.privateKey, keyEntry.certificateChain, keyEntry.attributes)
        keyStore.setEntry(generateAlias(prefix), newEntry, null)
        keyStore.deleteEntry(TEMP_ALIAS)
    }

    fun getPrivateKeyByPrefix(prefix: String): PrivateKey? {
        val keyEntry = keyStore.getEntry(generateAlias(prefix), null) as? PrivateKeyEntry
        return keyEntry?.privateKey
    }


    fun generateAlias(prefix: String): String {
        return KEYSTORE_ALIAS_PREFIX + prefix
    }

    fun containsAlias(alias: String): Boolean {
        return keyStore.containsAlias(alias)
    }

    fun getAllAliases(): List<String> {
        val aliases: MutableList<String> = ArrayList()
        val enumeration: Enumeration<String> = keyStore.aliases()
        while (enumeration.hasMoreElements()) {
            aliases.add(enumeration.nextElement())
        }
        return aliases
    }

    fun deleteEntry(alias: String): Boolean {
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
            return true
        }
        return false
    }

    fun clearAllEntries() {
        val aliases = getAllAliases()
        for (alias in aliases) {
            keyStore.deleteEntry(alias)
        }
    }

    fun loadKeyStore(password: CharArray) {
        val fileInputStream = FileInputStream(KEYSTORE_FILENAME)
        keyStore.load(fileInputStream, password)
        fileInputStream.close()
    }

    fun saveKeyStoreToFile(file: File, password: CharArray) {
        if (!file.exists()) {
            file.createNewFile()
        }
        val fileOutputStream = FileOutputStream(file)
        keyStore.store(fileOutputStream, password)
        fileOutputStream.close()
    }
}