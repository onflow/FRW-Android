package com.flowfoundation.wallet.manager.key

import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.AccountWalletManager
import com.flowfoundation.wallet.page.restore.keystore.PrivateKeyStoreCryptoProvider
import com.flowfoundation.wallet.wallet.Wallet
import io.outblock.wallet.CryptoProvider
import io.outblock.wallet.KeyStoreCryptoProvider


object CryptoProviderManager {

    private var cryptoProvider: CryptoProvider? = null

    fun getCurrentCryptoProvider(): CryptoProvider? {
        if (cryptoProvider == null) {
            cryptoProvider = generateAccountCryptoProvider(AccountManager.get())
        }
        return cryptoProvider
    }

    fun generateAccountCryptoProvider(account: Account?, isSwitch: Boolean = false): CryptoProvider? {
        if (account == null) {
            return null
        }
        if (account.keyStoreInfo.isNullOrBlank().not()) {
            return PrivateKeyStoreCryptoProvider(account.keyStoreInfo!!)
        } else if (account.prefix.isNullOrBlank().not()) {
            return KeyStoreCryptoProvider(account.prefix!!)
        } else {
            val wallet = if (account.isActive && isSwitch.not()) {
                Wallet.store().wallet()
            } else {
                AccountWalletManager.getHDWalletByUID(account.wallet?.id ?: "")
            }
            if (wallet == null) {
                return null
            }
            return HDWalletCryptoProvider(wallet)
        }
    }

    fun isHDWalletCrypto(): Boolean {
        return cryptoProvider != null && cryptoProvider is HDWalletCryptoProvider
    }

    fun clear() {
        cryptoProvider = null
    }
}