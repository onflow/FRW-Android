package com.flowfoundation.wallet.utils.secret

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.flowfoundation.wallet.utils.loge
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object EncryptedMnemonicUtils {
    private const val TAG = "EncryptedMnemonicUtils"
    private const val KEY_ALIAS_PREFIX = "mnemonic_key_"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    /**
     * Encrypt mnemonic using Android Keystore
     * @param mnemonic The mnemonic to encrypt
     * @param aliasSuffix Usually the user ID or a unique identifier
     * @return Base64 encoded encrypted mnemonic, or null if encryption fails
     */
    fun encrypt(mnemonic: String, aliasSuffix: String): String? {
        try {
            val keyAlias = "$KEY_ALIAS_PREFIX$aliasSuffix"

            // Get or create key from Android Keystore
            val secretKey = getOrCreateSecretKey(keyAlias)

            // AES/GCM encryption
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val encryptedData = cipher.doFinal(mnemonic.toByteArray())
            val iv = cipher.iv

            // Combine IV + Encrypted Data
            val combined = ByteArray(iv.size + encryptedData.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedData, 0, combined, iv.size, encryptedData.size)

            return Base64.encodeToString(combined, Base64.DEFAULT)

        } catch (e: Exception) {
            loge(TAG, "Failed to encrypt mnemonic: $e")
            return null
        }
    }

    /**
     * Decrypt mnemonic using Android Keystore
     * @param encryptedMnemonic Base64 encoded encrypted mnemonic
     * @param aliasSuffix Usually the user ID or a unique identifier
     * @return Decrypted mnemonic, or null if decryption fails
     */
    fun decrypt(encryptedMnemonic: String, aliasSuffix: String): String? {
        try {
            val keyAlias = "$KEY_ALIAS_PREFIX$aliasSuffix"
            val secretKey = getSecretKey(keyAlias) ?: return null

            val combined = Base64.decode(encryptedMnemonic, Base64.DEFAULT)

            // GCM IV is typically 12 bytes
            val ivSize = 12
            if (combined.size < ivSize) {
                loge(TAG, "Invalid encrypted data size")
                return null
            }

            val iv = combined.copyOfRange(0, ivSize)
            val encryptedData = combined.copyOfRange(ivSize, combined.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedData = cipher.doFinal(encryptedData)
            return String(decryptedData)

        } catch (e: Exception) {
            loge(TAG, "Failed to decrypt mnemonic: $e")
            return null
        }
    }

    private fun getOrCreateSecretKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        val existingKey = keyStore.getKey(alias, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    private fun getSecretKey(alias: String): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        return keyStore.getKey(alias, null) as? SecretKey
    }
}
