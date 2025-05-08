package com.flowfoundation.wallet.manager.account

import com.flow.wallet.CryptoProvider
import com.flow.wallet.keys.KeyProtocol
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.wallet.WalletFactory
import com.flow.wallet.wallet.WalletType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.flowfoundation.wallet.utils.readWalletPassword
import com.flowfoundation.wallet.manager.backup.BackupCryptoProvider
import com.flowfoundation.wallet.utils.Env.getStorage
import kotlinx.coroutines.runBlocking
import org.onflow.flow.ChainId
import com.flow.wallet.wallet.KeyWallet

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
        val seedPhraseKey = SeedPhraseKey(
            mnemonicString = password,
            passphrase = "",
            derivationPath = "m/44'/539'/0'/0/0",
            keyPair = null,
            storage = getStorage()
        )
        // Create a proper KeyWallet
        val wallet = WalletFactory.createKeyWallet(
            seedPhraseKey,
            setOf(ChainId.Mainnet, ChainId.Testnet),
            getStorage()
        )
        return BackupCryptoProvider(seedPhraseKey, wallet as KeyWallet)
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
        val storage = getStorage()
        val seedPhraseKey = runBlocking {
            try {
                // First try to restore as a keystore
                val key = PrivateKey.get(uid, password, storage)
                // Convert the private key to a seed phrase key
                SeedPhraseKey(
                    mnemonicString = key.exportPrivateKey(com.flow.wallet.keys.KeyFormat.RAW).toString(Charsets.UTF_8),
                    passphrase = "",
                    derivationPath = "m/44'/539'/0'/0/0",
                    keyPair = null,
                    storage = storage
                )
            } catch (e: Exception) {
                // If that fails, try to restore as a seed phrase
                SeedPhraseKey(
                    mnemonicString = password,
                    passphrase = "",
                    derivationPath = "m/44'/539'/0'/0/0",
                    keyPair = null,
                    storage = storage
                )
            }
        }
        // Create a proper KeyWallet using WalletFactory
        WalletFactory.createKeyWallet(
            seedPhraseKey,
            setOf(ChainId.Mainnet, ChainId.Testnet),
            storage
        )
        return BackupCryptoProvider(seedPhraseKey)
    }
}
