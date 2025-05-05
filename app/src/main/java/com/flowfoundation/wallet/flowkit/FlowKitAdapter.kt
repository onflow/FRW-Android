package com.flowfoundation.wallet.flowkit

import com.flow.wallet.CryptoProvider
import com.flow.wallet.keys.KeyProtocol
import com.flow.wallet.keys.KeyType
import com.flow.wallet.storage.FileSystemStorage
import com.flow.wallet.storage.StorageProtocol
import com.flow.wallet.wallet.Wallet
import com.flow.wallet.wallet.WalletFactory
import com.flow.wallet.wallet.WalletType
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import org.onflow.flow.ChainId
import java.io.File

/**
 * Adapter class to bridge between FRW-Android and Flow-Wallet-Kit implementations
 */
class FlowKitAdapter {
    private var wallet: Wallet? = null
    private val storage: StorageProtocol = FileSystemStorage(
        File(System.getProperty("java.io.tmpdir"), "flowkit_storage")
    )

    /**
     * Initialize the Flow-Wallet-Kit wallet based on the current account
     */
    fun initialize() {
        val currentAccount = AccountManager.get() ?: return
        val currentCryptoProvider = CryptoProviderManager.getCurrentCryptoProvider() ?: return

        // Convert existing crypto provider to Flow-Wallet-Kit key protocol
        val keyProtocol = convertToKeyProtocol(currentCryptoProvider)
        
        // Create appropriate wallet type
        wallet = when {
            currentAccount.prefix != null -> {
                // KeyStore wallet
                WalletFactory.createKeyWallet(
                    key = keyProtocol,
                    networks = setOf(ChainId.Mainnet, ChainId.Testnet),
                    storage = storage
                )
            }
            currentAccount.keyStoreInfo != null -> {
                // Private key wallet
                WalletFactory.createKeyWallet(
                    key = keyProtocol,
                    networks = setOf(ChainId.Mainnet, ChainId.Testnet),
                    storage = storage
                )
            }
            else -> {
                // HD wallet
                WalletFactory.createKeyWallet(
                    key = keyProtocol,
                    networks = setOf(ChainId.Mainnet, ChainId.Testnet),
                    storage = storage
                )
            }
        }

        // Migrate existing data
        migrateAccountData()
        migrateKeyData()
    }

    /**
     * Convert existing crypto provider to Flow-Wallet-Kit key protocol
     */
    private fun convertToKeyProtocol(cryptoProvider: CryptoProvider): KeyProtocol {
        return FlowKitKeyAdapter(cryptoProvider, storage)
    }

    /**
     * Get the current wallet instance
     */
    fun getWallet(): Wallet? = wallet

    /**
     * Migrate existing account data to Flow-Wallet-Kit format
     */
    private fun migrateAccountData() {
        // Migrate account cache
        AccountCacheManager.read()?.let { accounts ->
            accounts.forEach { account ->
                // TODO: Convert account data to Flow-Wallet-Kit format and store
                // This will be implemented when we add account data conversion
            }
        }

        // Migrate user prefix cache
        UserPrefixCacheManager.read()?.let { prefixes ->
            prefixes.forEach { prefix ->
                // TODO: Convert prefix data to Flow-Wallet-Kit format and store
                // This will be implemented when we add prefix data conversion
            }
        }
    }

    /**
     * Migrate existing key data to Flow-Wallet-Kit format
     */
    private fun migrateKeyData() {
        // TODO: Implement key data migration
        // This will be implemented when we add key data conversion
    }

    companion object {
        private var instance: FlowKitAdapter? = null

        fun getInstance(): FlowKitAdapter {
            if (instance == null) {
                instance = FlowKitAdapter()
            }
            return instance!!
        }
    }
} 