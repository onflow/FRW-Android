package com.flowfoundation.wallet.wallet

import com.nftco.flow.sdk.DomainTag.normalize
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.error.AccountError
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.wallet.KeyWallet
import com.flow.wallet.wallet.WalletFactory
import com.flow.wallet.errors.WalletError
import org.onflow.flow.ChainId
import com.flowfoundation.wallet.utils.Env.getStorage
import com.flowfoundation.wallet.manager.key.CryptoProviderManager

private const val DERIVATION_PATH = "m/44'/539'/0'/0/0"

fun getPublicKey(removePrefix: Boolean = true): String {
    return try {
        getKeyWallet()
        val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()
            ?: throw WalletError.InitHDWalletFailed
        val publicKey = cryptoProvider.getPublicKey()
        if (removePrefix) publicKey.removePrefix("04") else publicKey
    } catch (e: WalletError) {
        loge("WalletUtils", "Failed to get public key: ${e.message}")
        ErrorReporter.reportWithMixpanel(AccountError.WALLET_ERROR, e)
        ""
    } catch (e: Exception) {
        loge("WalletUtils", "Unexpected error getting public key: ${e.message}")
        ErrorReporter.reportWithMixpanel(AccountError.UNEXPECTED_ERROR, e)
        ""
    }
}

fun getKeyWallet(): KeyWallet {
    return try {
        val seedPhraseKey = SeedPhraseKey(
            mnemonicString = Wallet.store().mnemonic(),
            passphrase = "",
            derivationPath = DERIVATION_PATH,
            keyPair = null,
            storage = getStorage()
        )
        WalletFactory.createKeyWallet(
            seedPhraseKey,
            setOf(ChainId.Mainnet, ChainId.Testnet),
            getStorage()
        ) as KeyWallet
    } catch (e: WalletError) {
        loge("WalletUtils", "Failed to create key wallet: ${e.message}")
        ErrorReporter.reportWithMixpanel(AccountError.WALLET_ERROR, e)
        throw e
    } catch (e: Exception) {
        loge("WalletUtils", "Unexpected error creating key wallet: ${e.message}")
        ErrorReporter.reportWithMixpanel(AccountError.UNEXPECTED_ERROR, e)
        throw e
    }
}

suspend fun sign(text: String, domainTag: ByteArray = normalize("FLOW-V0.0-user")): String {
    return try {
        getKeyWallet()
        val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()
            ?: throw WalletError.InitHDWalletFailed
        cryptoProvider.signData(domainTag + text.encodeToByteArray())
    } catch (e: WalletError) {
        loge("WalletUtils", "Failed to sign text: ${e.message}")
        ErrorReporter.reportWithMixpanel(AccountError.WALLET_ERROR, e)
        ""
    } catch (e: Exception) {
        loge("WalletUtils", "Unexpected error signing text: ${e.message}")
        ErrorReporter.reportWithMixpanel(AccountError.UNEXPECTED_ERROR, e)
        ""
    }
}

suspend fun signData(data: ByteArray): String {
    return try {
        getKeyWallet()
        val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()
            ?: throw WalletError.InitHDWalletFailed
        cryptoProvider.signData(data)
    } catch (e: WalletError) {
        loge("WalletUtils", "Failed to sign data: ${e.message}")
        ErrorReporter.reportWithMixpanel(AccountError.WALLET_ERROR, e)
        ""
    } catch (e: Exception) {
        loge("WalletUtils", "Unexpected error signing data: ${e.message}")
        ErrorReporter.reportWithMixpanel(AccountError.UNEXPECTED_ERROR, e)
        ""
    }
}

fun createWalletFromServer() {
    ioScope {
        try {
            val service = retrofit().create(ApiService::class.java)
            val resp = service.createWallet()
            logd("createWalletFromServer", "$resp")
        } catch (e: Exception) {
            loge("WalletUtils", "Failed to create wallet from server: ${e.message}")
            ErrorReporter.reportWithMixpanel(AccountError.WALLET_ERROR, e)
        }
    }
}

fun String.toAddress(): String {
    val cleaned = removeAddressPrefix()
    return if (cleaned.startsWith("0x")) cleaned else "0x$cleaned"
}

fun String.removeAddressPrefix(): String = this.removePrefix("0x0x").removePrefix("0x").removePrefix("Fx")