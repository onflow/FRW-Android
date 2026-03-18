package com.flowfoundation.wallet.manager.evm

import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryCOATokenBalance
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.walletdata.EOAWallet
import com.flowfoundation.wallet.utils.formatPrice
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.retrofitApi
import java.math.BigDecimal

object DAppEVMConnectionManager {
    private val TAG = DAppEVMConnectionManager::class.java.simpleName
    private const val PREF_NAME = "dapp_evm_connection"
    private const val KEY_SELECTED_ACCOUNT = "selected_account"

    private val gson = Gson()

    private val _selectedAccount = MutableStateFlow<DAppEVMAccount?>(null)
    val selectedAccount: StateFlow<DAppEVMAccount?> = _selectedAccount.asStateFlow()

    private val _availableAccounts = MutableStateFlow<List<DAppEVMAccount>>(emptyList())
    val availableAccounts: StateFlow<List<DAppEVMAccount>> = _availableAccounts.asStateFlow()
    private val service by lazy { retrofitApi().create(ApiService::class.java) }

    init {
        loadSelectedAccount()
        loadAvailableAccounts()
    }

    /**
     * Get current selected account, fallback to COA if none selected
     */
    fun getCurrentAccount(): DAppEVMAccount? {
        val current = _selectedAccount.value
        if (current != null) return current

        // Fallback to COA account if available
        return _availableAccounts.value.find { it.type == DAppEVMAccountType.COA }
    }

    /**
     * Check if current selected account is EOA type
     */
    fun isCurrentEOAAccount(): Boolean {
        return getCurrentAccount()?.type == DAppEVMAccountType.EOA
    }

    /**
     * Set the selected account and persist it
     */
    fun setSelectedAccount(account: DAppEVMAccount) {
        logd(TAG, "Setting selected account: ${account.address} (${account.type})")
        _selectedAccount.value = account
        saveSelectedAccount(account)
    }

    /**
     * Load available accounts from wallet managers
     */
    private fun loadAvailableAccounts() {
        ioScope {
            val accounts = mutableListOf<DAppEVMAccount>()

            try {
                // Load COA account
                val coaAddress = EVMWalletManager.getEVMAddress()
                if (!coaAddress.isNullOrEmpty()) {
                    logd(TAG, "Found COA account: $coaAddress")

                    // Query COA balance
                    var rawBalance: BigDecimal? = null
                    val coaBalance = try {
                        rawBalance = cadenceQueryCOATokenBalance()
                        rawBalance?.formatPrice(includeSymbol = true)
                    } catch (e: Exception) {
                        logd(TAG, "Error querying COA balance: ${e.message}")
                        null
                    }

                    val coaAccount = DAppEVMAccount(coaAddress, DAppEVMAccountType.COA, coaBalance)
                    accounts.add(coaAccount)

                    // Check if COA should be auto-selected (Balance > 0 or has NFTs)
                    val hasBalance = rawBalance != null && rawBalance > BigDecimal.ZERO
                    var hasNFTs = false

                    if (!hasBalance) {
                        try {
                            val nftResponse = service.getEVMNFTCollections(coaAddress)
                            val totalNftCount = nftResponse.data?.sumOf { it.count ?: 0 } ?: 0
                            hasNFTs = nftResponse.data?.isNotEmpty() == true && totalNftCount > 0
                        } catch (e: Exception) {
                             // Ignore NFT API errors
                        }
                    }

                    if (hasBalance || hasNFTs) {
                        logd(TAG, "COA account has balance or NFTs, setting as selected: $coaAddress")
                        setSelectedAccount(coaAccount)
                    }
                }

                // Load all EOA accounts
                val eoaWallets = AccountManager.walletNodes()
                    ?.filterIsInstance<EOAWallet>() ?: emptyList()
                eoaWallets.forEach { eoaWallet ->
                    logd(TAG, "Found EOA account: ${eoaWallet.address} (index=${eoaWallet.index})")
                    accounts.add(DAppEVMAccount(eoaWallet.address, DAppEVMAccountType.EOA, null))
                }

                _availableAccounts.value = accounts
                logd(TAG, "Loaded ${accounts.size} available accounts")

                // Set default selected account if none is selected
                if (_selectedAccount.value == null && accounts.isNotEmpty()) {
                    // Default to COA account, fallback to first available
                    val defaultAccount = accounts.find { it.type == DAppEVMAccountType.COA } ?: accounts.first()
                    setSelectedAccount(defaultAccount)
                    logd(TAG, "Set default selected account: ${defaultAccount.address} (${defaultAccount.type}) with balance: ${defaultAccount.balance}")
                }

            } catch (e: Exception) {
                logd(TAG, "Error loading available accounts: ${e.message}")
            }
        }
    }

    /**
     * Refresh available accounts (call this when account status changes)
     */
    fun refreshAccounts() {
        logd(TAG, "Refreshing available accounts")
        loadAvailableAccounts()
    }

    private fun saveSelectedAccount(account: DAppEVMAccount) {
        try {
            val prefs = Env.getApp().getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
            val json = gson.toJson(account)
            prefs.edit { putString(KEY_SELECTED_ACCOUNT, json) }
            logd(TAG, "Saved selected account to preferences")
        } catch (e: Exception) {
            logd(TAG, "Error saving selected account: ${e.message}")
        }
    }

    private fun loadSelectedAccount() {
        try {
            val prefs = Env.getApp().getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_SELECTED_ACCOUNT, null)
            if (!json.isNullOrEmpty()) {
                val account = gson.fromJson(json, DAppEVMAccount::class.java)
                _selectedAccount.value = account
                logd(TAG, "Loaded selected account from preferences: ${account.address} (${account.type})")
            } else {
                logd(TAG, "No saved selected account found")
            }
        } catch (e: Exception) {
            logd(TAG, "Error loading selected account: ${e.message}")
        }
    }

    /**
     * Clear saved preferences (useful for testing or reset)
     */
    fun clearPreferences() {
        try {
            val prefs = Env.getApp().getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            _selectedAccount.value = null
            logd(TAG, "Cleared preferences")
        } catch (e: Exception) {
            logd(TAG, "Error clearing preferences: ${e.message}")
        }
    }
}
