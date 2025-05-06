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
import com.flow.wallet.storage.FileSystemStorage
import com.flowfoundation.wallet.utils.Env
import java.io.File

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
            val baseDir = File(Env.getApp().filesDir, "wallet")
            val privateKey = PrivateKey.create(FileSystemStorage(baseDir))
            return BackupCryptoProvider(SeedPhraseKey(
                mnemonicString = privateKey.exportPrivateKey(KeyFormat.RAW).toString(Charsets.UTF_8),
                passphrase = "",
                derivationPath = "m/44'/539'/0'/0/0",
                keyPair = null,
                storage = FileSystemStorage(baseDir)
            ))
        } else {
            val wallet = if (account.isActive) {
                Wallet.store().wallet()
            } else {
                AccountWalletManager.getHDWalletByUID(account.wallet?.id ?: "")
            }
            if (wallet == null) {
                return null
            }
            val baseDir = File(Env.getApp().filesDir, "wallet")
            val backupProvider = BackupCryptoProvider(SeedPhraseKey(
                mnemonicString = (wallet as BackupCryptoProvider).getMnemonic(),
                passphrase = "",
                derivationPath = "m/44'/539'/0'/0/0",
                keyPair = null,
                storage = FileSystemStorage(baseDir)
            ))
            return backupProvider
        }
    }

    fun getSwitchAccountCryptoProvider(account: Account): CryptoProvider? {
        if (account.keyStoreInfo.isNullOrBlank().not()) {
            return PrivateKeyStoreCryptoProvider(account.keyStoreInfo!!)
        } else if (account.prefix.isNullOrBlank().not()) {
            val baseDir = File(Env.getApp().filesDir, "wallet")
            val privateKey = PrivateKey.create(FileSystemStorage(baseDir))
            return BackupCryptoProvider(SeedPhraseKey(
                mnemonicString = privateKey.exportPrivateKey(KeyFormat.RAW).toString(Charsets.UTF_8),
                passphrase = "",
                derivationPath = "m/44'/539'/0'/0/0",
                keyPair = null,
                storage = FileSystemStorage(baseDir)
            ))
        } else {
            val wallet = AccountWalletManager.getHDWalletByUID(account.wallet?.id ?: "")
            if (wallet == null) {
                return null
            }
            val baseDir = File(Env.getApp().filesDir, "wallet")
            val backupProvider = BackupCryptoProvider(SeedPhraseKey(
                mnemonicString = (wallet as BackupCryptoProvider).getMnemonic(),
                passphrase = "",
                derivationPath = "m/44'/539'/0'/0/0",
                keyPair = null,
                storage = FileSystemStorage(baseDir)
            ))
            return backupProvider
        }
    }

    fun getSwitchAccountCryptoProvider(switchAccount: LocalSwitchAccount): CryptoProvider? {
        if (switchAccount.prefix.isNullOrBlank().not()) {
            val baseDir = File(Env.getApp().filesDir, "wallet")
            val privateKey = PrivateKey.create(FileSystemStorage(baseDir))
            return BackupCryptoProvider(SeedPhraseKey(
                mnemonicString = privateKey.exportPrivateKey(KeyFormat.RAW).toString(Charsets.UTF_8),
                passphrase = "",
                derivationPath = "m/44'/539'/0'/0/0",
                keyPair = null,
                storage = FileSystemStorage(baseDir)
            ))
        } else {
            val wallet = AccountWalletManager.getHDWalletByUID(switchAccount.userId ?: "")
            if (wallet == null) {
                return null
            }
            val baseDir = File(Env.getApp().filesDir, "wallet")
            val backupProvider = BackupCryptoProvider(SeedPhraseKey(
                mnemonicString = (wallet as BackupCryptoProvider).getMnemonic(),
                passphrase = "",
                derivationPath = "m/44'/539'/0'/0/0",
                keyPair = null,
                storage = FileSystemStorage(baseDir)
            ))
            return backupProvider
        }
    }

    fun clear() {
        cryptoProvider = null
    }
}