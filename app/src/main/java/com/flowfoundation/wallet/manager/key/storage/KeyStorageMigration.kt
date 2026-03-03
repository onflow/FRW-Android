package com.flowfoundation.wallet.manager.key.storage

import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.AccountWalletManager
import com.flowfoundation.wallet.page.restore.keystore.model.KeystoreAddress
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.secret.EncryptedMnemonicUtils
import com.google.gson.Gson

/**
 * One-time, idempotent migration that copies key material from Account-bound storage
 * into the independent [KeyStorageManager] stores.
 *
 * This is safe to call on every app start because each account is only written once
 * (we skip accounts whose keys are already present in the new stores).
 *
 * Migration rules (per account, uid = account.wallet?.id):
 *   1. keyStoreInfo != null AND encryptedMnemonic != null
 *      → decrypt mnemonic and write to SP store
 *   2. keyStoreInfo != null AND privateKey != null (no encrypted mnemonic)
 *      → write raw private key hex to PK store
 *   3. prefix != null (Android Keystore / legacy prefix key)
 *      → write prefix to AKP store
 *   4. None of the above (plain mnemonic account)
 *      → fetch mnemonic from AccountWalletManager and write to SP store
 */
object KeyStorageMigration {

    private const val TAG = "KeyStorageMigration"

    fun runMigrationIfNeeded() {
        try {
            val accounts = AccountManager.list()
            if (accounts.isEmpty()) {
                logd(TAG, "No accounts found, skipping migration")
                return
            }
            logd(TAG, "Starting key storage migration for ${accounts.size} account(s)")
            for (account in accounts) {
                val uid = account.wallet?.id
                if (uid.isNullOrBlank()) {
                    logd(TAG, "Skipping account ${account.userInfo.username}: no wallet ID")
                    continue
                }
                try {
                    migrateAccount(uid, account)
                } catch (e: Exception) {
                    loge(TAG, "Failed to migrate account $uid: ${e.message}")
                }
            }
            logd(TAG, "Key storage migration completed")
        } catch (e: Exception) {
            loge(TAG, "Migration failed: ${e.message}")
        }
    }

    private fun migrateAccount(uid: String, account: Account) {
        val keyStoreInfo = account.keyStoreInfo
        val prefix = account.prefix

        when {
            // Case 1 & 2: account has keyStoreInfo
            !keyStoreInfo.isNullOrBlank() -> migrateFromKeyStoreInfo(uid, keyStoreInfo)

            // Case 3: prefix-based Android Keystore / legacy key
            !prefix.isNullOrBlank() -> {
                if (!KeyStorageManager.hasAndroidKeystorePrefix(uid)) {
                    KeyStorageManager.saveAndroidKeystorePrefix(uid, prefix)
                    logd(TAG, "Migrated prefix → AKP for uid: $uid")
                } else {
                    logd(TAG, "AKP already exists for uid: $uid, skipping")
                }
            }

            // Case 4: plain HD-wallet mnemonic account
            else -> migrateFromAccountWalletManager(uid)
        }
    }

    private fun migrateFromKeyStoreInfo(uid: String, keyStoreInfo: String) {
        val ks = try {
            Gson().fromJson(keyStoreInfo, KeystoreAddress::class.java)
        } catch (e: Exception) {
            loge(TAG, "Failed to parse keyStoreInfo for uid $uid: ${e.message}")
            return
        }

        when {
            // encryptedMnemonic present → seed-phrase restore
            !ks.encryptedMnemonic.isNullOrBlank() -> {
                if (!KeyStorageManager.hasSeedPhrase(uid)) {
                    val mnemonic = EncryptedMnemonicUtils.decrypt(ks.encryptedMnemonic, uid)
                    if (!mnemonic.isNullOrBlank()) {
                        KeyStorageManager.saveSeedPhrase(uid, mnemonic)
                        logd(TAG, "Migrated encryptedMnemonic → SP for uid: $uid")
                    } else {
                        loge(TAG, "Failed to decrypt mnemonic for uid: $uid")
                    }
                } else {
                    logd(TAG, "SP already exists for uid: $uid, skipping")
                }
            }

            // privateKey present → private-key import
            !ks.privateKey.isNullOrBlank() -> {
                if (!KeyStorageManager.hasPrivateKey(uid)) {
                    val hex = ks.privateKey.removePrefix("0x")
                    KeyStorageManager.savePrivateKey(uid, hex)
                    logd(TAG, "Migrated privateKey → PK for uid: $uid")
                } else {
                    logd(TAG, "PK already exists for uid: $uid, skipping")
                }
            }

            else -> logd(TAG, "keyStoreInfo for uid $uid has neither encryptedMnemonic nor privateKey")
        }
    }

    private fun migrateFromAccountWalletManager(uid: String) {
        if (!KeyStorageManager.hasSeedPhrase(uid)) {
            val mnemonic = AccountWalletManager.getHDWalletMnemonicByUID(uid)
            if (!mnemonic.isNullOrBlank()) {
                KeyStorageManager.saveSeedPhrase(uid, mnemonic)
                logd(TAG, "Migrated AccountWalletManager mnemonic → SP for uid: $uid")
            } else {
                logd(TAG, "No mnemonic found in AccountWalletManager for uid: $uid")
            }
        } else {
            logd(TAG, "SP already exists for uid: $uid, skipping")
        }
    }
}
