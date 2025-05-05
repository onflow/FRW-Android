package com.flowfoundation.wallet.manager.key

import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.AccountWalletManager
import com.flowfoundation.wallet.manager.account.model.LocalSwitchAccount
import com.flowfoundation.wallet.page.restore.keystore.PrivateKeyStoreCryptoProvider
import com.flowfoundation.wallet.wallet.Wallet
import com.flow.wallet.CryptoProvider
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.storage.FileSystemStorage

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
            return PrivateKey.create(FileSystemStorage())
        } else {
            val wallet = if (account.isActive) {
                Wallet.store().wallet()
            } else {
                AccountWalletManager.getHDWalletByUID(account.wallet?.id ?: "")
            }
            if (wallet == null) {
                return null
            }
            return SeedPhraseKey(
                mnemonicString = wallet.mnemonic(),
                passphrase = "",
                derivationPath = "m/44'/539'/0'/0/0",
                keyPair = null,
                storage = FileSystemStorage()
            )
        }
    }

    fun getSwitchAccountCryptoProvider(account: Account): CryptoProvider? {
        if (account.keyStoreInfo.isNullOrBlank().not()) {
            return PrivateKeyStoreCryptoProvider(account.keyStoreInfo!!)
        } else if (account.prefix.isNullOrBlank().not()) {
            return PrivateKey.create(FileSystemStorage())
        } else {
            val wallet = AccountWalletManager.getHDWalletByUID(account.wallet?.id ?: "")
            if (wallet == null) {
                return null
            }
            return SeedPhraseKey(
                mnemonicString = wallet.mnemonic(),
                passphrase = "",
                derivationPath = "m/44'/539'/0'/0/0",
                keyPair = null,
                storage = FileSystemStorage()
            )
        }
    }

    fun getSwitchAccountCryptoProvider(switchAccount: LocalSwitchAccount): CryptoProvider? {
        if (switchAccount.prefix.isNullOrBlank().not()) {
            return PrivateKey.create(FileSystemStorage())
        } else {
            val wallet = AccountWalletManager.getHDWalletByUID(switchAccount.userId ?: "")
            if (wallet == null) {
                return null
            }
            return SeedPhraseKey(
                mnemonicString = wallet.mnemonic(),
                passphrase = "",
                derivationPath = "m/44'/539'/0'/0/0",
                keyPair = null,
                storage = FileSystemStorage()
            )
        }
    }

    fun clear() {
        cryptoProvider = null
    }
}