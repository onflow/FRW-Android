package com.flowfoundation.wallet.utils

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.WorkerThread
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Crypto SharedPreferences
 */

// mnemonic
private const val KEY_MNEMONIC = "key_mnemonic"
private const val KEY_BACKUP_MNEMONIC = "key_backup_mnemonic"
private const val KEY_PUSH_TOKEN = "push_token"
private const val KEY_WALLET_PASSWORD = "key_wallet_password"
private const val KEY_PIN_CODE = "key_pin_code"
private const val KEY_WALLET_STORE_NAME_AES_KEY = "key_wallet_store_name_aes_key"

private const val KEY_AES_LOCAL_CODE = "key_aes_local_code"

private val preference by lazy {
    try {
        EncryptedSharedPreferences.create(
            Env.getApp(),
            "safe_preference",
            MasterKey.Builder(Env.getApp()).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        Env.getApp().getSharedPreferences("safe_backup_preference", Context.MODE_PRIVATE)
    }

}

fun storeWalletPassword(key: String) {
    preference.edit().putString(KEY_WALLET_PASSWORD, key).apply()
}

fun readWalletPassword(): String = preference.getString(KEY_WALLET_PASSWORD, "").orEmpty()

@SuppressLint("ApplySharedPref")
@WorkerThread
fun savePinCode(key: String) {
    preference.edit().putString(KEY_PIN_CODE, key).apply()
}

fun getPinCode(): String = preference.getString(KEY_PIN_CODE, "").orEmpty()

fun getPushToken(): String = preference.getString(KEY_PUSH_TOKEN, "").orEmpty()

fun updatePushToken(token: String) {
    preference.edit().putString(KEY_PUSH_TOKEN, token).apply()
}

fun saveWalletStoreNameAesKey(key: String) {
    preference.edit().putString(KEY_WALLET_STORE_NAME_AES_KEY, key).apply()
}

fun getWalletStoreNameAesKey(): String = preference.getString(KEY_WALLET_STORE_NAME_AES_KEY, "").orEmpty()

/** TODO delete this **/
fun getMnemonicFromPreferenceV0(): String = preference.getString(KEY_MNEMONIC, "").orEmpty()

fun cleanMnemonicPreferenceV0() {
    preference.edit().putString(KEY_MNEMONIC, "").apply()
}

fun saveBackupMnemonicToPreference(mnemonic: String) {
    preference.edit().putString(KEY_BACKUP_MNEMONIC, mnemonic).apply()
}

fun cleanBackupMnemonicPreference() {
    preference.edit().putString(KEY_BACKUP_MNEMONIC, "").apply()
}

fun updateAesLocalCodeV0(key: String) {
    preference.edit().putString(KEY_AES_LOCAL_CODE, key).apply()
}

fun getAesLocalCodeV0(): String = preference.getString(KEY_AES_LOCAL_CODE, "").orEmpty()
/** TODO delete this **/
