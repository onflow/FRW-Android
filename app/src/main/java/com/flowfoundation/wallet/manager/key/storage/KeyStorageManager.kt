package com.flowfoundation.wallet.manager.key.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.flow.wallet.keys.CryptoProviderKey
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.storage.FileSystemStorage
import com.flow.wallet.storage.StorageProtocol
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.wallet.DERIVATION_PATH
import kotlinx.coroutines.runBlocking
import org.onflow.flow.models.bytesToHex
import org.onflow.flow.models.hexToBytes
import java.io.File
import java.security.SecureRandom
import androidx.core.content.edit

/**
 * Independent key storage manager that decouples key material from the Account object.
 *
 * Mirrors the original three-bucket layout but uses [FileSystemStorage] directories
 * instead of SharedPreferences files, and delegates encryption to FlowWalletKit primitives.
 *
 * Physical storage layout:
 *   filesDir/frw_sp_storage/{uid}   – SeedPhraseKey JSON encrypted with ChaCha20
 *   filesDir/frw_pk_storage/{uid}   – raw PrivateKey bytes encrypted with ChaCha20
 *   filesDir/frw_akp_storage/{uid}  – Android Keystore prefix string encrypted with ChaCha20
 *
 * Each key type lives in its own dedicated directory (one-type-per-directory), exactly
 * matching the old SharedPreferences-per-type separation:
 *   frw_sp_storage.xml  →  filesDir/frw_sp_storage/
 *   frw_pk_storage.xml  →  filesDir/frw_pk_storage/
 *   frw_akp_storage.xml →  filesDir/frw_akp_storage/
 *
 * The ChaCha20 password is a random hex string generated once and stored in
 * [EncryptedSharedPreferences] under `frw_key_master_password` (hardware-backed AES-256-GCM).
 */
object KeyStorageManager {

    private const val TAG = "KeyStorageManager"

    // Storage directory names – intentionally match the old SharedPreferences names
    private const val SP_STORAGE_NAME  = "frw_sp_storage"
    private const val PK_STORAGE_NAME  = "frw_pk_storage"
    private const val AKP_STORAGE_NAME = "frw_akp_storage"

    private const val MASTER_PASSWORD_PREFS = "frw_key_master_password"
    private const val MASTER_PASSWORD_KEY   = "password"

    // ─── Independent per-type StorageProtocol instances ──────────────────────

    /** One FileSystemStorage per key type – mirrors the old one-SharedPreferences-per-type design. */
    private val spStorage: StorageProtocol by lazy {
        FileSystemStorage(File(Env.getApp().filesDir, SP_STORAGE_NAME))
    }
    private val pkStorage: StorageProtocol by lazy {
        FileSystemStorage(File(Env.getApp().filesDir, PK_STORAGE_NAME))
    }
    private val akpStorage: StorageProtocol by lazy {
        FileSystemStorage(File(Env.getApp().filesDir, AKP_STORAGE_NAME))
    }

    // ─── Master password (EncryptedSharedPreferences) ────────────────────────

    private val masterPasswordPrefs: SharedPreferences by lazy {
        createEncryptedPreference(MASTER_PASSWORD_PREFS)
    }

    private fun createEncryptedPreference(name: String): SharedPreferences = try {
        EncryptedSharedPreferences.create(
            Env.getApp(),
            name,
            MasterKey.Builder(Env.getApp())
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        logd(TAG, "EncryptedSharedPreferences creation failed, using fallback: ${e.message}")
        Env.getApp().getSharedPreferences("${name}_backup", Context.MODE_PRIVATE)
    }

    /** Returns the persistent master password, generating it once on first call. */
    @Synchronized
    fun getOrCreateMasterPassword(): String {
        var password = masterPasswordPrefs.getString(MASTER_PASSWORD_KEY, null)
        if (password.isNullOrBlank()) {
            password = ByteArray(32).also { SecureRandom().nextBytes(it) }.bytesToHex()
            masterPasswordPrefs.edit { putString(MASTER_PASSWORD_KEY, password) }
            logd(TAG, "Generated new master password")
        }
        return password
    }

    private fun password() = getOrCreateMasterPassword()

    // ─── Seed Phrase  (filesDir/frw_sp_storage/{uid}) ────────────────────────

    /**
     * Encrypts and stores [mnemonic] in the dedicated seed-phrase directory.
     * The stored entry also includes derivation path and passphrase for full reconstruction.
     */
    fun saveSeedPhrase(uid: String, mnemonic: String) {
        try {
            val key = SeedPhraseKey(
                mnemonicString = mnemonic,
                passphrase = "",
                derivationPath = DERIVATION_PATH,
                storage = spStorage
            )
            runBlocking { key.store(uid, password()) }
            logd(TAG, "Saved seed phrase for uid: $uid")
        } catch (e: Exception) {
            loge(TAG, "Failed to save seed phrase for uid $uid: ${e.message}")
        }
    }

    /**
     * Returns a fully initialised [SeedPhraseKey] ready for signing / wallet creation.
     * Returns null if nothing was stored for [uid] or if decryption fails.
     */
    fun getSeedPhraseKey(uid: String): SeedPhraseKey? =
        SeedPhraseKey.load(uid, password(), spStorage)

    /** Convenience: returns the raw mnemonic string. Prefer [getSeedPhraseKey] where possible. */
    fun getSeedPhrase(uid: String): String? =
        getSeedPhraseKey(uid)?.mnemonic?.joinToString(" ")

    fun hasSeedPhrase(uid: String): Boolean = spStorage.get(uid) != null

    /** Returns all UIDs that have a stored seed phrase. */
    fun getAllSeedPhraseUids(): List<String> = spStorage.allKeys

    // ─── Private Key  (filesDir/frw_pk_storage/{uid}) ────────────────────────

    /**
     * Encrypts and stores the private key in the dedicated private-key directory.
     * [privateKeyHex] must be a 64-char lowercase hex string (32 bytes, no "0x" prefix).
     */
    fun savePrivateKey(uid: String, privateKeyHex: String) {
        try {
            val key = PrivateKey.restore(privateKeyHex.hexToBytes(), pkStorage)
            runBlocking { key.store(uid, password()) }
            logd(TAG, "Saved private key for uid: $uid")
        } catch (e: Exception) {
            loge(TAG, "Failed to save private key for uid $uid: ${e.message}")
        }
    }

    /**
     * Returns a fully initialised [PrivateKey] object ready for wallet creation.
     * Returns null if nothing was stored for [uid] or if decryption fails.
     */
    fun getPrivateKeyObject(uid: String): PrivateKey? = try {
        PrivateKey.get(uid, password(), pkStorage)
    } catch (e: Exception) { null }

    /** Convenience: returns the raw private key as a lowercase hex string. */
    fun getPrivateKey(uid: String): String? =
        getPrivateKeyObject(uid)?.secret?.bytesToHex()

    fun hasPrivateKey(uid: String): Boolean = pkStorage.get(uid) != null

    /** Returns all UIDs that have a stored private key. */
    fun getAllPrivateKeyUids(): List<String> = pkStorage.allKeys

    // ─── Android Keystore Prefix  (filesDir/frw_akp_storage/{uid}) ───────────

    /**
     * Encrypts and stores the Android Keystore [prefix] string in the dedicated AKP directory.
     * The provider is reconstructed from the prefix at the app layer when the wallet is created.
     */
    fun saveAndroidKeystorePrefix(uid: String, prefix: String) {
        try {
            CryptoProviderKey.saveProviderIdentifier(uid, prefix, password(), akpStorage)
            logd(TAG, "Saved Android Keystore prefix for uid: $uid")
        } catch (e: Exception) {
            loge(TAG, "Failed to save AK prefix for uid $uid: ${e.message}")
        }
    }

    fun getAndroidKeystorePrefix(uid: String): String? =
        CryptoProviderKey.getProviderIdentifier(uid, password(), akpStorage)

    fun hasAndroidKeystorePrefix(uid: String): Boolean =
        CryptoProviderKey.hasProviderIdentifier(uid, akpStorage)

    /** Returns all UIDs that have a stored Android Keystore prefix. */
    fun getAllAndroidKeystoreUids(): List<String> = akpStorage.allKeys
}
