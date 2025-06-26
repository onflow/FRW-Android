package com.flowfoundation.wallet.manager.account

import com.flow.wallet.CryptoProvider
import com.flow.wallet.keys.SeedPhraseKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.flowfoundation.wallet.utils.readWalletPassword
import com.flowfoundation.wallet.manager.key.HDWalletCryptoProvider
import com.flowfoundation.wallet.utils.Env.getStorage

/**
 * Manages wallet creation and access using Flow-Wallet-Kit
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
        
        // Create SeedPhraseKey from the original mnemonic using Flow-Wallet-Kit
        val seedPhraseKey = SeedPhraseKey(
            mnemonicString = password,
            passphrase = "",
            derivationPath = "m/44'/539'/0'/0/0",
            keyPair = null,
            storage = getStorage()
        )
        
        // Return HDWalletCryptoProvider with weight 1000 for original seed phrase
        return HDWalletCryptoProvider(seedPhraseKey)
    }

}

