package com.flowfoundation.wallet.wallet

import com.flow.wallet.wallet.KeyWallet
import com.flow.wallet.wallet.ProxyWallet
import com.flow.wallet.wallet.Wallet
import com.flow.wallet.storage.FileSystemStorage
import com.flow.wallet.storage.StorageProtocol
import com.flowfoundation.wallet.utils.DATA_PATH
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.onflow.flow.ChainId
import java.io.File

/**
 * Manages wallet operations using the Flow Wallet Kit SDK
 */
class WalletManager {
    private val storage: StorageProtocol = FileSystemStorage(
        File(DATA_PATH, "wallet_storage")
    )
    
    private val _activeWallet = MutableStateFlow<Wallet?>(null)
    val activeWallet: StateFlow<Wallet?> = _activeWallet
    
    /**
     * Creates a new key-based wallet using the existing WalletStore
     * @param networks Set of networks to support (defaults to mainnet and testnet)
     */
    fun createKeyWallet(networks: Set<ChainId> = setOf(ChainId.Mainnet, ChainId.Testnet)): Wallet {
        // Get the mnemonic from the existing WalletStore
        val mnemonic = Wallet.store().mnemonic()
        
        // Create a key using the existing mnemonic
        val key = KeyManager().createSeedPhraseKey(mnemonic)
        
        // Create and return the wallet
        return KeyWallet(key, networks, storage).also {
            _activeWallet.value = it
        }
    }
    
    /**
     * Creates a new proxy wallet for hardware devices
     * @param networks Set of networks to support (defaults to mainnet and testnet)
     */
    fun createProxyWallet(networks: Set<ChainId> = setOf(ChainId.Mainnet, ChainId.Testnet)): Wallet {
        return ProxyWallet(networks, storage).also {
            _activeWallet.value = it
        }
    }
    
    /**
     * Fetches accounts for the active wallet
     */
    suspend fun fetchAccounts() {
        _activeWallet.value?.fetchAccounts()
    }
    
    /**
     * Fetches accounts for a specific network
     * @param network The network to fetch accounts from
     */
    suspend fun fetchAccountsForNetwork(network: ChainId) {
        _activeWallet.value?.fetchAccountsForNetwork(network)
    }
    
    /**
     * Adds a network to the active wallet
     * @param network The network to add
     */
    suspend fun addNetwork(network: ChainId) {
        _activeWallet.value?.addNetwork(network)
    }
    
    /**
     * Removes a network from the active wallet
     * @param network The network to remove
     */
    suspend fun removeNetwork(network: ChainId) {
        _activeWallet.value?.removeNetwork(network)
    }
} 