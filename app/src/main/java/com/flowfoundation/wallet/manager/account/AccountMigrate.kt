package com.flowfoundation.wallet.manager.account

import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nftco.flow.sdk.hexToBytes
import com.flowfoundation.wallet.cache.userInfoCache
import com.flowfoundation.wallet.cache.walletCache
import com.flowfoundation.wallet.firebase.auth.isAnonymousSignIn
import com.flowfoundation.wallet.utils.DATA_PATH
import com.flowfoundation.wallet.utils.getWalletStoreNameAesKey
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.readWalletPassword
import com.flowfoundation.wallet.utils.saveWalletStoreNameAesKey
import com.flowfoundation.wallet.utils.secret.aesDecrypt
import com.flowfoundation.wallet.utils.secret.aesEncrypt
import com.flowfoundation.wallet.wallet.WalletStore
import wallet.core.jni.StoredKey
import java.io.File
import java.util.UUID

// from single account to multi account
fun accountMigrateV1(callback: (() -> Unit)? = null) {
    ioScope {
        if (!isAccountV1DataExist()) {
            callback?.invoke()
            return@ioScope
        }

        migrateV1()
    }
}

fun migrateV1() {
    logd("xxx", "migrate start")
    val account = Account(
        userInfo = userInfoCache().read()!!,
        isActive = true,
        wallet = walletCache().read()
    )
    AccountManager.add(account)
    userInfoCache().clear()
    WalletStoreMigrate().mnemonic()

    logd("xxx", "migrate end username:${account.userInfo.username}")
}

fun isAccountV1DataExist() = userInfoCache().isCacheExist()


private val TAG = WalletStore::class.java.simpleName
private const val TEMP_STORE = "temp"

private class WalletStoreMigrate {

    private var keyStore: StoredKey
    private var password: ByteArray

    init {
        password = password()
        keyStore = generateKeyStore()
    }

    fun mnemonic(): String = keyStore.decryptMnemonic(password)

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

private fun readCurrentUserPassword(): String? {
    val uid = uid() ?: return null
    return passwordMap()[uid]
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