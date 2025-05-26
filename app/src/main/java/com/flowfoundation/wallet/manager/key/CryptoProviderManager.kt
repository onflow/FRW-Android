package com.flowfoundation.wallet.manager.key

import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.AccountWalletManager
import com.flowfoundation.wallet.manager.account.model.LocalSwitchAccount
import com.flowfoundation.wallet.manager.backup.BackupCryptoProvider
import com.flowfoundation.wallet.manager.key.PrivateKeyCryptoProvider
import com.flowfoundation.wallet.page.restore.keystore.PrivateKeyStoreCryptoProvider
import com.flowfoundation.wallet.wallet.Wallet
import com.flow.wallet.CryptoProvider
import com.flow.wallet.keys.KeyFormat
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.wallet.KeyWallet
import com.flow.wallet.wallet.WalletFactory
import com.flow.wallet.errors.WalletError
import com.flowfoundation.wallet.manager.flow.FlowCadenceApi
import com.flowfoundation.wallet.utils.Env.getStorage
import com.flowfoundation.wallet.utils.error.AccountError
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import org.onflow.flow.ChainId
import org.onflow.flow.models.SigningAlgorithm
import kotlinx.coroutines.runBlocking

object CryptoProviderManager {

    private var cryptoProvider: CryptoProvider? = null

    fun getCurrentCryptoProvider(): CryptoProvider? {
        if (cryptoProvider == null) {
            logd("CryptoProviderManager", "in getCurrentCryptoProvider")
            cryptoProvider = generateAccountCryptoProvider(AccountManager.get())
            logd("CryptoProviderManager", cryptoProvider)
        }
        return cryptoProvider
    }

    fun generateAccountCryptoProvider(account: Account?): CryptoProvider? {
        if (account == null) {
            logd("CryptoProviderManager", "account is null")
            return null
        }
        logd("CryptoProviderManager", "Generating crypto provider for account: ${account.userInfo.username}")
        logd("CryptoProviderManager", "Account prefix: ${account.prefix}")
        logd("CryptoProviderManager", "Account keystore info: ${account.keyStoreInfo}")
        logd("CryptoProviderManager", "Account wallet: ${account.wallet}")

        val storage = getStorage()

        return try {
            // Handle keystore-based accounts
            if (account.keyStoreInfo.isNullOrBlank().not()) {
                logd("CryptoProviderManager", "Creating keystore-based crypto provider")
                PrivateKeyStoreCryptoProvider(account.keyStoreInfo!!)
            }

            // Handle prefix-based accounts
            else if (account.prefix.isNullOrBlank().not()) {
                logd("CryptoProviderManager", "Creating prefix-based crypto provider")
                
                // Load the stored private key using the prefix-based ID
                val keyId = "prefix_key_${account.prefix}"
                val privateKey = try {
                    PrivateKey.get(keyId, account.prefix!!, storage)
                } catch (e: Exception) {
                    loge("CryptoProviderManager", "CRITICAL ERROR: Failed to load stored private key for prefix ${account.prefix}: ${e.message}")
                    loge("CryptoProviderManager", "Cannot proceed without the stored key as it would create a different account")
                    return null // Return null instead of creating a new key
                }
                logd("CryptoProviderManager", "Successfully loaded private key for prefix: ${account.prefix}")
                
                // Create a proper KeyWallet directly with the PrivateKey
                val wallet = WalletFactory.createKeyWallet(
                    privateKey,
                    setOf(ChainId.Mainnet, ChainId.Testnet),
                    storage
                ) as KeyWallet
                
                // For prefix-based accounts, we use PrivateKeyCryptoProvider instead of BackupCryptoProvider
                PrivateKeyCryptoProvider(privateKey, wallet)
            }

            // Handle active accounts
            else if (account.isActive) {
                logd("CryptoProviderManager", "Creating active account crypto provider")
                val seedPhraseKey = SeedPhraseKey(
                    mnemonicString = Wallet.store().mnemonic(),
                    passphrase = "",
                    derivationPath = "m/44'/539'/0'/0/0",
                    keyPair = null,
                    storage = storage
                )
                // Create a proper KeyWallet
                val wallet = WalletFactory.createKeyWallet(
                    seedPhraseKey,
                    setOf(ChainId.Mainnet, ChainId.Testnet),
                    storage
                ) as KeyWallet

                // Get the account's keys to determine the signing algorithm
                val accountKeys = account.wallet?.walletAddress()
                    ?.let { runBlocking { FlowCadenceApi.getAccount(it).keys } }
                val signingAlgorithm = if (accountKeys?.any { it.signingAlgorithm == SigningAlgorithm.ECDSA_secp256k1 } == true) {
                    SigningAlgorithm.ECDSA_secp256k1
                } else {
                    SigningAlgorithm.ECDSA_P256
                }
                logd("CryptoProviderManager", "Using signing algorithm: $signingAlgorithm")

                BackupCryptoProvider(seedPhraseKey, wallet, signingAlgorithm)
            }

            // Handle inactive accounts
            else {
                logd("CryptoProviderManager", "Creating inactive account crypto provider")
                val existingWallet = AccountWalletManager.getHDWalletByUID(account.wallet?.id ?: "")
                if (existingWallet == null) {
                    loge("CryptoProviderManager", "Failed to get existing wallet for account ${account.userInfo.username}")
                    ErrorReporter.reportWithMixpanel(AccountError.GET_WALLET_FAILED)
                    return null
                }

                val seedPhraseKey = SeedPhraseKey(
                    mnemonicString = (existingWallet as BackupCryptoProvider).getMnemonic(),
                    passphrase = "",
                    derivationPath = "m/44'/539'/0'/0/0",
                    keyPair = null,
                    storage = storage
                )
                // Create a proper KeyWallet
                val wallet = WalletFactory.createKeyWallet(
                    seedPhraseKey,
                    setOf(ChainId.Mainnet, ChainId.Testnet),
                    storage
                ) as KeyWallet

                // Get the account's keys to determine the signing algorithm
                val accountKeys = account.wallet?.walletAddress()
                    ?.let { runBlocking { FlowCadenceApi.getAccount(it).keys } }
                val signingAlgorithm = if (accountKeys?.any { it.signingAlgorithm == SigningAlgorithm.ECDSA_secp256k1 } == true) {
                    SigningAlgorithm.ECDSA_secp256k1
                } else {
                    SigningAlgorithm.ECDSA_P256
                }
                logd("CryptoProviderManager", "Using signing algorithm: $signingAlgorithm")

                BackupCryptoProvider(seedPhraseKey, wallet, signingAlgorithm)
            }
        } catch (e: WalletError) {
            loge("CryptoProviderManager", "Wallet error: ${e.message}")
            ErrorReporter.reportWithMixpanel(AccountError.WALLET_ERROR, e)
            null
        } catch (e: Exception) {
            loge("CryptoProviderManager", "Unexpected error: ${e.message}")
            ErrorReporter.reportWithMixpanel(AccountError.UNEXPECTED_ERROR, e)
            null
        }
    }

    fun getSwitchAccountCryptoProvider(account: Account): CryptoProvider? {
        val storage = getStorage()

        return try {
            // Handle keystore-based accounts
            if (account.keyStoreInfo.isNullOrBlank().not()) {
                PrivateKeyStoreCryptoProvider(account.keyStoreInfo!!)
            }

            // Handle prefix-based accounts
            else if (account.prefix.isNullOrBlank().not()) {
                // Load the stored private key using the prefix-based ID
                val keyId = "prefix_key_${account.prefix}"
                val privateKey = try {
                    PrivateKey.get(keyId, account.prefix!!, storage)
                } catch (e: Exception) {
                    loge("CryptoProviderManager", "CRITICAL ERROR: Failed to load stored private key for switch account prefix ${account.prefix}: ${e.message}")
                    loge("CryptoProviderManager", "Cannot proceed without the stored key as it would create a different account")
                    return null // Return null instead of creating a new key
                }
                
                // Create a proper KeyWallet directly with the PrivateKey
                val wallet = WalletFactory.createKeyWallet(
                    privateKey,
                    setOf(ChainId.Mainnet, ChainId.Testnet),
                    storage
                ) as KeyWallet
                
                // For prefix-based accounts, we use PrivateKeyCryptoProvider instead of BackupCryptoProvider
                PrivateKeyCryptoProvider(privateKey, wallet)
            }

            // Handle other accounts
            else {
                val existingWallet = AccountWalletManager.getHDWalletByUID(account.wallet?.id ?: "")
                if (existingWallet == null) {
                    loge("CryptoProviderManager", "Failed to get existing wallet for account ${account.userInfo.username}")
                    ErrorReporter.reportWithMixpanel(AccountError.GET_WALLET_FAILED)
                    return null
                }

                val seedPhraseKey = SeedPhraseKey(
                    mnemonicString = (existingWallet as BackupCryptoProvider).getMnemonic(),
                    passphrase = "",
                    derivationPath = "m/44'/539'/0'/0/0",
                    keyPair = null,
                    storage = storage
                )
                // Create a proper KeyWallet
                val wallet = WalletFactory.createKeyWallet(
                    seedPhraseKey,
                    setOf(ChainId.Mainnet, ChainId.Testnet),
                    storage
                ) as KeyWallet
                BackupCryptoProvider(seedPhraseKey, wallet)
            }
        } catch (e: WalletError) {
            loge("CryptoProviderManager", "Wallet error: ${e.message}")
            ErrorReporter.reportWithMixpanel(AccountError.WALLET_ERROR, e)
            null
        } catch (e: Exception) {
            loge("CryptoProviderManager", "Unexpected error: ${e.message}")
            ErrorReporter.reportWithMixpanel(AccountError.UNEXPECTED_ERROR, e)
            null
        }
    }

    fun getSwitchAccountCryptoProvider(switchAccount: LocalSwitchAccount): CryptoProvider? {
        val storage = getStorage()

        return try {
            // Handle prefix-based accounts
            if (switchAccount.prefix.isNullOrBlank().not()) {
                // Load the stored private key using the prefix-based ID
                val keyId = "prefix_key_${switchAccount.prefix}"
                val privateKey = try {
                    PrivateKey.get(keyId, switchAccount.prefix!!, storage)
                } catch (e: Exception) {
                    loge("CryptoProviderManager", "CRITICAL ERROR: Failed to load stored private key for local switch account prefix ${switchAccount.prefix}: ${e.message}")
                    loge("CryptoProviderManager", "Cannot proceed without the stored key as it would create a different account")
                    return null // Return null instead of creating a new key
                }
                
                // Create a proper KeyWallet directly with the PrivateKey
                val wallet = WalletFactory.createKeyWallet(
                    privateKey,
                    setOf(ChainId.Mainnet, ChainId.Testnet),
                    storage
                ) as KeyWallet
                
                // For prefix-based accounts, we use PrivateKeyCryptoProvider instead of BackupCryptoProvider
                PrivateKeyCryptoProvider(privateKey, wallet)
            }

            // Handle other accounts
            else {
                val existingWallet = AccountWalletManager.getHDWalletByUID(switchAccount.userId ?: "")
                if (existingWallet == null) {
                    loge("CryptoProviderManager", "Failed to get existing wallet for switch account ${switchAccount.username}")
                    ErrorReporter.reportWithMixpanel(AccountError.GET_WALLET_FAILED)
                    return null
                }

                val seedPhraseKey = SeedPhraseKey(
                    mnemonicString = (existingWallet as BackupCryptoProvider).getMnemonic(),
                    passphrase = "",
                    derivationPath = "m/44'/539'/0'/0/0",
                    keyPair = null,
                    storage = storage
                )
                // Create a proper KeyWallet
                val wallet = WalletFactory.createKeyWallet(
                    seedPhraseKey,
                    setOf(ChainId.Mainnet, ChainId.Testnet),
                    storage
                ) as KeyWallet
                BackupCryptoProvider(seedPhraseKey, wallet)
            }
        } catch (e: WalletError) {
            loge("CryptoProviderManager", "Wallet error: ${e.message}")
            ErrorReporter.reportWithMixpanel(AccountError.WALLET_ERROR, e)
            null
        } catch (e: Exception) {
            loge("CryptoProviderManager", "Unexpected error: ${e.message}")
            ErrorReporter.reportWithMixpanel(AccountError.UNEXPECTED_ERROR, e)
            null
        }
    }

    fun clear() {
        cryptoProvider = null
    }
}