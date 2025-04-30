package com.flowfoundation.wallet.manager.account

import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.wallet.Wallet
import com.flowfoundation.wallet.wallet.AccountManager as FlowAccountManager
import com.flowfoundation.wallet.wallet.KeyManager
import org.onflow.flow.ChainId
import org.onflow.flow.models.SigningAlgorithm

object AccountMigrate {
    private val TAG = AccountMigrate::class.java.simpleName
    
    // New Flow Wallet Kit SDK instances
    private val wallet = Wallet()
    private val accountManager = FlowAccountManager(wallet)
    private val keyManager = KeyManager()

    suspend fun migrateAccounts() {
        try {
            logd(TAG, "Starting account migration")
            
            // Fetch accounts for all networks
            accountManager.fetchAccounts()
            
            // Get the current accounts
            val accounts = accountManager.accounts.first()
            
            // Log migration results
            accounts.forEach { (network, accountList) ->
                logd(TAG, "Migrated ${accountList.size} accounts for network: $network")
            }
            
            logd(TAG, "Account migration completed successfully")
        } catch (e: Exception) {
            loge(TAG, "Error during account migration: ${e.message}")
            throw e
        }
    }

    suspend fun migrateNetwork(network: ChainId) {
        try {
            logd(TAG, "Starting network migration for: $network")
            
            // Fetch accounts for specific network
            accountManager.fetchAccountsForNetwork(network)
            
            // Get the current accounts
            val accounts = accountManager.accounts.first()
            val accountList = accounts[network] ?: emptyList()
            
            logd(TAG, "Migrated ${accountList.size} accounts for network: $network")
        } catch (e: Exception) {
            loge(TAG, "Error during network migration: ${e.message}")
            throw e
        }
    }
}