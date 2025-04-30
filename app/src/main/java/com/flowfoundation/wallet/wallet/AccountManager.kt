package com.flowfoundation.wallet.wallet

import com.flowfoundation.wallet.utils.logd
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.onflow.flow.ChainId
import org.onflow.flow.models.Account

/**
 * Manages account operations using the Flow Wallet Kit SDK
 */
class AccountManager(private val wallet: Wallet) {
    private val _accounts = MutableStateFlow<Map<ChainId, List<Account>>>(emptyMap())
    val accounts: StateFlow<Map<ChainId, List<Account>>> = _accounts
    
    /**
     * Fetches all accounts for all networks
     */
    suspend fun fetchAccounts() {
        logd(TAG, "Fetching accounts for all networks")
        wallet.fetchAccounts()
        _accounts.value = wallet.accounts
    }
    
    /**
     * Fetches accounts for a specific network
     * @param network The network to fetch accounts from
     */
    suspend fun fetchAccountsForNetwork(network: ChainId) {
        logd(TAG, "Fetching accounts for network: $network")
        wallet.fetchAccountsForNetwork(network)
        _accounts.value = wallet.accounts
    }
    
    /**
     * Gets an account by address
     * @param address The account address
     * @return The account if found, null otherwise
     */
    suspend fun getAccount(address: String): Account? {
        logd(TAG, "Getting account for address: $address")
        return wallet.getAccount(address)
    }
    
    /**
     * Adds an account to the wallet
     * @param account The account to add
     */
    suspend fun addAccount(account: Account) {
        logd(TAG, "Adding account: ${account.address}")
        wallet.addAccount(account)
        _accounts.value = wallet.accounts
    }
    
    /**
     * Removes an account from the wallet
     * @param address The address of the account to remove
     */
    suspend fun removeAccount(address: String) {
        logd(TAG, "Removing account: $address")
        wallet.removeAccount(address)
        _accounts.value = wallet.accounts
    }
    
    /**
     * Refreshes all accounts
     */
    suspend fun refreshAccounts() {
        logd(TAG, "Refreshing all accounts")
        wallet.refreshAccounts()
        _accounts.value = wallet.accounts
    }
    
    companion object {
        private const val TAG = "AccountManager"
    }
} 