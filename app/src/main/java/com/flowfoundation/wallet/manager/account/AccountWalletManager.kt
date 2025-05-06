package com.flowfoundation.wallet.manager.account

import com.flow.wallet.CryptoProvider
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.storage.FileSystemStorage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.flowfoundation.wallet.utils.readWalletPassword
import com.flowfoundation.wallet.manager.backup.BackupCryptoProvider
import com.flowfoundation.wallet.utils.Env
import java.io.File

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

    fun getHDWalletByUID(uid: String): CryptoProvider? {
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
                uid to wallet.getPublicKey()
            } else {
                null
            }
        }.toMap()
    }
}

class WalletStoreWithUid(private val uid: String, private val password: String) {
    fun wallet(): CryptoProvider {
        val baseDir = File(Env.getApp().filesDir, "wallet")
        val storage = FileSystemStorage(baseDir)
        val seedPhraseKey = SeedPhraseKey(
            mnemonicString = password,
            passphrase = "",
            derivationPath = "m/44'/539'/0'/0/0",
            keyPair = null,
            storage = storage
        )
        return BackupCryptoProvider(seedPhraseKey)
    }
}
