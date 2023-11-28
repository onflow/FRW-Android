package io.outblock.lilico.manager.key

import io.outblock.lilico.manager.account.Account
import io.outblock.lilico.manager.account.AccountManager
import io.outblock.lilico.manager.account.AccountWalletManager
import io.outblock.lilico.wallet.Wallet
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

    fun generateAccountCryptoProvider(account: Account?): CryptoProvider? {
        if (account == null) {
            return null
        }
        if (account.prefix.isNullOrBlank()) {
            val wallet = if (account.isActive) {
                Wallet.store().wallet()
            } else {
                AccountWalletManager.getHDWalletByUID(account.wallet?.id ?: "")
            }
            if (wallet == null) {
                return null
            }
            return HDWalletCryptoProvider(wallet)
        } else {
            return KeyStoreCryptoProvider(account.prefix!!)
        }
    }

    fun clear() {
        cryptoProvider = null
    }
}