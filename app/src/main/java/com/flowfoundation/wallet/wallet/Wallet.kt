package com.flowfoundation.wallet.wallet

import com.flow.wallet.wallet.KeyWallet
import com.flow.wallet.storage.FileSystemStorage
import com.flow.wallet.storage.StorageProtocol
import com.flowfoundation.wallet.utils.DATA_PATH
import com.flowfoundation.wallet.utils.logd
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.onflow.flow.ChainId
import java.io.File

/**
 * Main wallet class that manages Flow accounts using the Flow Wallet Kit SDK
 */
class Wallet {
    private val storage: StorageProtocol = FileSystemStorage(
        File(DATA_PATH, "wallet_storage")
    )
    
    private val _activeWallet = MutableStateFlow<KeyWallet?>(null)
    val activeWallet: StateFlow<KeyWallet?> = _activeWallet
    
    /**
     * Creates a new wallet with a seed phrase
     * @param mnemonic The mnemonic phrase to use
     * @param networks Set of networks to support (defaults to mainnet and testnet)
     */
    fun createWallet(mnemonic: String, networks: Set<ChainId> = setOf(ChainId.Mainnet, ChainId.Testnet)) {
        logd(TAG, "Creating new wallet with mnemonic")
        val key = KeyManager().createSeedPhraseKey(mnemonic)
        _activeWallet.value = KeyWallet(key, networks, storage)
    }
    
    /**
     * Gets the mnemonic phrase for the current wallet
     * @return The mnemonic phrase if available, null otherwise
     */
    fun getMnemonic(): String? {
        val wallet = _activeWallet.value as? KeyWallet ?: return null
        return wallet.getKeyForAccount().mnemonic
    }
    
    /**
     * Fetches accounts for the active wallet
     */
    suspend fun fetchAccounts() {
        _activeWallet.value?.accounts
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
    
    companion object {
        private const val TAG = "Wallet"
    }
}