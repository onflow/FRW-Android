package com.flowfoundation.wallet.manager.account

import com.flowfoundation.wallet.manager.key.HDWalletCryptoProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nftco.flow.sdk.hexToBytes
import com.flowfoundation.wallet.utils.DATA_PATH
import com.flowfoundation.wallet.utils.getWalletStoreNameAesKey
import com.flowfoundation.wallet.utils.readWalletPassword
import com.flowfoundation.wallet.utils.saveWalletStoreNameAesKey
import com.flowfoundation.wallet.utils.secret.aesEncrypt
import wallet.core.jni.HDWallet
import wallet.core.jni.StoredKey
import java.io.File
import java.util.UUID

/**
 * Created by Mengxy on 8/30/23.
 */
object AccountWalletManager {

    private fun passwordMap(): HashMap<String, String> {
        val pref = runCatching { readWalletPassword() }.getOrNull()
        return if (pref.isNullOrBlank()) {
            HashMap()
        } else {
            Gson().fromJson(pref, object : TypeToken<HashMap<String, String>>() {}.type)
        }
    }

    fun getHDWalletByUID(uid: String): HDWallet? {
        val password = passwordMap()[uid]
        if (password.isNullOrBlank()) {
            return null
        }
        return WalletStoreWithUid(uid, password).wallet()
    }

    fun getUIDPublicKeyMap(): Map<String, String> {
        return passwordMap().mapNotNull { (uid, _) ->
            val wallet = getHDWalletByUID(uid)
            if (wallet != null) {
                uid to HDWalletCryptoProvider(wallet).getPublicKey()
            } else {
                null
            }
        }.toMap()
    }
}


class WalletStoreWithUid(private val uid: String, private val password: String) {
    private var keyStore: StoredKey

    init {
        keyStore = generateKeyStore()
    }

    fun wallet(): HDWallet = keyStore.wallet(password.hexToBytes())

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
