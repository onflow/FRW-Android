package com.flowfoundation.wallet.manager.account

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.flowfoundation.wallet.utils.readWalletPassword
import com.flowfoundation.wallet.utils.storeWalletPassword
import com.flowfoundation.wallet.utils.DATA_PATH
import com.flowfoundation.wallet.utils.getWalletStoreNameAesKey
import com.flowfoundation.wallet.utils.saveWalletStoreNameAesKey
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.secret.aesEncrypt
import com.nftco.flow.sdk.hexToBytes
import com.nftco.flow.sdk.bytesToHex
import wallet.core.jni.CoinType
import wallet.core.jni.HDWallet
import wallet.core.jni.StoredKey
import java.io.File
import java.util.UUID

/**
 * Manages wallet creation and access using Flow-Wallet-Kit
 */
object AccountWalletManager {

    private const val TAG = "AccountWalletManager"

    private fun passwordMap(): HashMap<String, String> {
        val pref = runCatching { readWalletPassword() }.getOrNull()
        return if (pref.isNullOrBlank()) {
            HashMap()
        } else {
            Gson().fromJson(pref, object : TypeToken<HashMap<String, String>>() {}.type)
        }
    }

    private fun savePasswordMap(map: HashMap<String, String>) {
        storeWalletPassword(Gson().toJson(map))
    }

    /**
     * Stores a mnemonic for a given user ID.
     * This is the proper way to store mnemonics for RN seed phrase accounts.
     * 
     * @param uid The user ID (wallet.id from the backend)
     * @param mnemonic The mnemonic phrase to store
     * @return true if successfully stored, false otherwise
     */
    fun storeHDWalletMnemonic(uid: String, mnemonic: String): Boolean {
        return try {
            logd(TAG, "storeHDWalletMnemonic() - Storing mnemonic for UID: $uid")
            
            // Generate a random password for encryption
            val password = UUID.randomUUID().toString().replace("-", "").take(32)
            val passwordBytes = password.toByteArray().bytesToHex()
            
            // Store password in the password map
            val map = passwordMap()
            map[uid] = passwordBytes
            savePasswordMap(map)
            
            // Create StoredKey with the existing mnemonic
            val storeName = aesEncrypt(key = getOrCreateStoreNameAesKey(), message = uid)
            val storePath = File(DATA_PATH, storeName).absolutePath
            
            // Use StoredKey.importHDWallet to store an existing mnemonic
            // Note: wallet.core.jni uses CoinType.ETHEREUM for Flow-compatible mnemonics
            val storedKey = StoredKey.importHDWallet(
                mnemonic,
                storeName,
                passwordBytes.hexToBytes(),
                CoinType.ETHEREUM
            )
            
            // Export and save to disk
            val exportedData = storedKey.exportJSON()
            File(storePath).writeBytes(exportedData)
            
            logd(TAG, "storeHDWalletMnemonic() - Successfully stored mnemonic for UID: $uid")
            true
        } catch (e: Exception) {
            loge(TAG, "storeHDWalletMnemonic() - Failed to store mnemonic: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    fun getHDWalletMnemonicByUID(uid: String): String? {
        val password = passwordMap()[uid]
        if (password.isNullOrBlank()) {
            return null
        }
        val walletStore = WalletStoreWithUid(uid, password)
        return walletStore.getMnemonic()
    }

    fun isHDWallet(uid: String): Boolean {
        return !passwordMap()[uid].isNullOrBlank()
    }

    /**
     * Safely checks if an HD wallet keystore file exists for the given UID.
     * This method does NOT create a new keystore if one doesn't exist.
     * 
     * @return true if both the password exists in the map AND the keystore file exists on disk
     */
    fun hasHDWalletKeystore(uid: String): Boolean {
        val password = passwordMap()[uid]
        if (password.isNullOrBlank()) {
            return false
        }
        // Compute the store path without creating a WalletStoreWithUid (which would create the file)
        val storeName = aesEncrypt(key = getOrCreateStoreNameAesKey(), message = uid)
        val storePath = File(DATA_PATH, storeName).absolutePath
        return File(storePath).exists()
    }

    /**
     * Get the AES key for store name encryption, creating one if it doesn't exist.
     * This is extracted from WalletStoreWithUid to allow safe file existence checks.
     */
    private fun getOrCreateStoreNameAesKey(): String {
        var local = getWalletStoreNameAesKey()
        if (local.isBlank()) {
            local = UUID.randomUUID().toString().take(16)
            saveWalletStoreNameAesKey(local)
        }
        return local
    }


    class WalletStoreWithUid(private val uid: String, private val password: String) {
        private var keyStore: StoredKey

        init {
            keyStore = generateKeyStore()
        }

        fun wallet(): HDWallet = keyStore.wallet(password.hexToBytes())

        fun getMnemonic(): String = keyStore.decryptMnemonic(password.hexToBytes())

        private fun generateKeyStore(): StoredKey {
            return if (!File(storePath()).exists()) {
                StoredKey(storeName(), password.hexToBytes())
            } else {
                StoredKey.load(storePath())
            }
        }

        private fun storePath() = File(DATA_PATH, storeName()).absolutePath

        private fun storeName() = aesEncrypt(key = storeNameAesKey(), message = uid)

        private fun storeNameAesKey(): String {
            var local = getWalletStoreNameAesKey()
            if (local.isBlank()) {
                local = randomString()
                saveWalletStoreNameAesKey(local)
            }
            return local
        }

        private fun randomString(length: Int = 16): String = UUID.randomUUID().toString().take(length)
    }
}

