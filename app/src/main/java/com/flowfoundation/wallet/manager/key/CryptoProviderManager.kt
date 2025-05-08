package com.flowfoundation.wallet.manager.key

import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.AccountWalletManager
import com.flowfoundation.wallet.manager.account.model.LocalSwitchAccount
import com.flowfoundation.wallet.manager.backup.BackupCryptoProvider
import com.flowfoundation.wallet.page.restore.keystore.PrivateKeyStoreCryptoProvider
import com.flowfoundation.wallet.wallet.Wallet
import com.flow.wallet.CryptoProvider
import com.flow.wallet.keys.KeyFormat
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.keys.SeedPhraseKey
import com.flowfoundation.wallet.utils.Env.getStorage

object CryptoProviderManager {

    private var cryptoProvider: CryptoProvider? = null

    fun getCurrentCryptoProvider(): CryptoProvider? {
        if (cryptoProvider == null) {
            cryptoProvider = generateAccountCryptoProvider(AccountManager.get())
        }
        return cryptoProvider
    }

    fun generateAccountCryptoProvider(account: Account?): CryptoProvider? {
        if (account == null) {
            return null
        }
        if (account.keyStoreInfo.isNullOrBlank().not()) {
            return PrivateKeyStoreCryptoProvider(account.keyStoreInfo!!)
        } else if (account.prefix.isNullOrBlank().not()) {
            val storage = getStorage()
            val privateKey = PrivateKey.create(storage)
            return BackupCryptoProvider(SeedPhraseKey(
                mnemonicString = privateKey.exportPrivateKey(KeyFormat.RAW).toString(Charsets.UTF_8),
                passphrase = "",
                derivationPath = "m/44'/539'/0'/0/0",
                keyPair = null,
                storage = storage
            ))
        } else {
            val storage = getStorage()
            return if (account.isActive) {
                val wallet = Wallet.store().wallet()
                val seedPhraseKey = SeedPhraseKey(
                    mnemonicString = Wallet.store().mnemonic(),
                    passphrase = "",
                    derivationPath = "m/44'/539'/0'/0/0",
                    keyPair = null,
                    storage = storage
                )
                BackupCryptoProvider(seedPhraseKey)
            } else {
                val wallet = AccountWalletManager.getHDWalletByUID(account.wallet?.id ?: "")
                if (wallet == null) {
                    null
                } else {
                    val seedPhraseKey = (wallet as BackupCryptoProvider).getMnemonic()
                    BackupCryptoProvider(SeedPhraseKey(
                        mnemonicString = seedPhraseKey,
                        passphrase = "",
                        derivationPath = "m/44'/539'/0'/0/0",
                        keyPair = null,
                        storage = storage
                    ))
                }
            }
        }
    }

    fun getSwitchAccountCryptoProvider(account: Account): CryptoProvider? {
        if (account.keyStoreInfo.isNullOrBlank().not()) {
            return PrivateKeyStoreCryptoProvider(account.keyStoreInfo!!)
        } else if (account.prefix.isNullOrBlank().not()) {
            val storage = getStorage()
            val privateKey = PrivateKey.create(storage)
            return BackupCryptoProvider(SeedPhraseKey(
                mnemonicString = privateKey.exportPrivateKey(KeyFormat.RAW).toString(Charsets.UTF_8),
                passphrase = "",
                derivationPath = "m/44'/539'/0'/0/0",
                keyPair = null,
                storage = storage
            ))
        } else {
            val storage = getStorage()
            val wallet = AccountWalletManager.getHDWalletByUID(account.wallet?.id ?: "")
            if (wallet == null) {
                return null
            }
            return BackupCryptoProvider(SeedPhraseKey(
                mnemonicString = (wallet as BackupCryptoProvider).getMnemonic(),
                passphrase = "",
                derivationPath = "m/44'/539'/0'/0/0",
                keyPair = null,
                storage = storage
            ))
        }
    }

    fun getSwitchAccountCryptoProvider(switchAccount: LocalSwitchAccount): CryptoProvider? {
        if (switchAccount.prefix.isNullOrBlank().not()) {
            val storage = getStorage()
            val privateKey = PrivateKey.create(storage)
            return BackupCryptoProvider(SeedPhraseKey(
                mnemonicString = privateKey.exportPrivateKey(KeyFormat.RAW).toString(Charsets.UTF_8),
                passphrase = "",
                derivationPath = "m/44'/539'/0'/0/0",
                keyPair = null,
                storage = storage
            ))
        } else {
            val storage = getStorage()
            val wallet = AccountWalletManager.getHDWalletByUID(switchAccount.userId ?: "")
            if (wallet == null) {
                return null
            }
            return BackupCryptoProvider(SeedPhraseKey(
                mnemonicString = (wallet as BackupCryptoProvider).getMnemonic(),
                passphrase = "",
                derivationPath = "m/44'/539'/0'/0/0",
                keyPair = null,
                storage = storage
            ))
        }
    }

    fun clear() {
        cryptoProvider = null
    }
}