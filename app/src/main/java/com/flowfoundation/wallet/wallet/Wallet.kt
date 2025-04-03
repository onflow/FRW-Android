package com.flowfoundation.wallet.wallet

import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nftco.flow.sdk.bytesToHex
import com.nftco.flow.sdk.hexToBytes
import com.flowfoundation.wallet.firebase.auth.isAnonymousSignIn
import com.flowfoundation.wallet.utils.DATA_PATH
import com.flowfoundation.wallet.utils.getWalletStoreNameAesKey
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.logw
import com.flowfoundation.wallet.utils.readWalletPassword
import com.flowfoundation.wallet.utils.saveWalletStoreNameAesKey
import com.flowfoundation.wallet.utils.secret.aesDecrypt
import com.flowfoundation.wallet.utils.secret.aesEncrypt
import com.flowfoundation.wallet.utils.storeWalletPassword
import wallet.core.jni.CoinType
import wallet.core.jni.HDWallet
import wallet.core.jni.StoredKey
import java.io.File
import java.util.UUID

private val TAG = WalletStore::class.java.simpleName

private const val TEMP_STORE = "temp"

object Wallet {

    private val store by lazy { WalletStore() }

    fun store() = store
}

class WalletStore internal constructor() {

    private var keyStore: StoredKey
    private var password: ByteArray

    init {
        password = password()
        keyStore = generateKeyStore()
    }

    fun updateMnemonic(mnemonic: String) = apply {
        logd(TAG, "updateMnemonic")
        password = password()
        keyStore = keyStore.changeMnemonic(mnemonic, password)
    }

    fun reset(mnemonic: String) {
        password = password()
        keyStore = generateKeyStore().changeMnemonic(mnemonic, password)
        store()
    }

    fun resume() {
        password = password()
        keyStore = generateKeyStore()
    }

    fun store() = apply {
        if (uid().isNullOrBlank()) {
            logw(TAG, "user not sign in, can't store")
            return@apply
        }
        logd(TAG, "store")

        if (keyStore.name() != storeName()) {
            keyStore = keyStore.changeName(storeName(), password)
        }

        saveCurrentUserPassword(password.bytesToHex())
        keyStore.store(storePath())
    }

    fun mnemonic(): String = keyStore.decryptMnemonic(password)

    fun wallet(): HDWallet = keyStore.wallet(password)

    private fun generateKeyStore(): StoredKey {
        val uid = uid()
        return if (uid.isNullOrBlank() || !File(storePath()).exists()) {
            StoredKey(TEMP_STORE, password)
        } else {
            logd(TAG, "origin uid: ${uid()}")
            logd(TAG, "getUidFromStoreName: ${getUidFromStoreName()}")
            StoredKey.load(storePath())
        }
    }

    private fun password() = readCurrentUserPassword()?.hexToBytes() ?: randomString().toByteArray()

    private fun storePath() = File(DATA_PATH, storeName()).absolutePath

    private fun storeName() = aesEncrypt(key = storeNameAesKey(), message = uid()!!)

    private fun getUidFromStoreName() = aesDecrypt(key = storeNameAesKey(), message = storeName())
}

private fun StoredKey.changeName(name: String, password: ByteArray): StoredKey {
    return StoredKey.importHDWallet(decryptMnemonic(password), name, password, CoinType.FLOW)
}

private fun StoredKey.changeMnemonic(mnemonic: String, password: ByteArray): StoredKey {
    return StoredKey.importHDWallet(mnemonic, name(), password, CoinType.FLOW)
}

private fun readCurrentUserPassword(): String? {
    val uid = uid() ?: return null
    return passwordMap()[uid]
}

private fun saveCurrentUserPassword(password: String) {
    val uid = uid() ?: return
    val passwordMap = passwordMap()
    passwordMap[uid] = password
    storeWalletPassword(Gson().toJson(passwordMap))
}

private fun passwordMap(): HashMap<String, String> {
    val pref = runCatching { readWalletPassword() }.getOrNull()
    return if (pref.isNullOrBlank()) {
        HashMap()
    } else {
        Gson().fromJson(pref, object : TypeToken<HashMap<String, String>>() {}.type)
    }
}

private fun storeNameAesKey(): String {
    var local = getWalletStoreNameAesKey()
    if (local.isBlank()) {
        local = randomString()
        saveWalletStoreNameAesKey(local)
    }
    return local
}

private fun randomString(length: Int = 16): String = UUID.randomUUID().toString().take(length)

private fun uid() = if (isAnonymousSignIn()) null else Firebase.auth.currentUser?.uid