package com.flowfoundation.wallet.manager.account

import android.content.Context
import android.content.SharedPreferences
import com.flowfoundation.wallet.firebase.auth.firebaseUid
import com.flowfoundation.wallet.manager.app.AppLifecycleObserver
import com.flowfoundation.wallet.utils.Env
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manages account visibility state
 * Storage structure: Map<UserId, List<Address>>
 * UserId - User ID, Address - Hidden wallet address
 */
object AccountVisibilityManager {

    private const val PREF_NAME = "account_visibility"
    private const val KEY_HIDDEN_ACCOUNTS = "hidden_accounts"

    private var sharedPreferences: SharedPreferences? = null
    private val gson = Gson()

    // In-memory cache, format: Map<UserId, Set<Address>>
    private var hiddenAccountsCache = mutableMapOf<String, MutableSet<String>>()

    fun init() {
        if (sharedPreferences == null) {
            sharedPreferences = Env.getApp().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            loadHiddenAccounts()
        }
    }

    /**
     * Load hidden account data from SharedPreferences
     */
    private fun loadHiddenAccounts() {
        val json = sharedPreferences?.getString(KEY_HIDDEN_ACCOUNTS, null)
        if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<Map<String, List<String>>>() {}.type
                val loadedData: Map<String, List<String>> = gson.fromJson(json, type)

                // Convert to in-memory cache format
                hiddenAccountsCache.clear()
                loadedData.forEach { (userId, addresses) ->
                    hiddenAccountsCache[userId] = addresses.toMutableSet()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                hiddenAccountsCache.clear()
            }
        }
    }

    /**
     * Save hidden account data to SharedPreferences
     */
    private fun saveHiddenAccounts() {
        try {
            // Convert to serializable format
            val dataToSave = hiddenAccountsCache.mapValues { it.value.toList() }
            val json = gson.toJson(dataToSave)
            sharedPreferences?.edit()?.putString(KEY_HIDDEN_ACCOUNTS, json)?.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Get hidden account list for current user
     */
    fun getHiddenAccounts(userId: String): Set<String> {
        return hiddenAccountsCache[userId]?.toSet() ?: emptySet()
    }

    /**
     * Check if the specified address is hidden
     */
    fun isAccountHidden(userId: String, address: String): Boolean {
        return hiddenAccountsCache[userId]?.contains(address) ?: false
    }

    fun isCurrentProfileAccountHidden(address: String): Boolean {
        val userId = firebaseUid() ?: return false
        return isAccountHidden(userId, address)
    }

    /**
     * Hide specified account
     */
    fun hideAccount(userId: String, address: String) {
        if (hiddenAccountsCache[userId] == null) {
            hiddenAccountsCache[userId] = mutableSetOf()
        }

        if (hiddenAccountsCache[userId]!!.add(address)) {
            saveHiddenAccounts()
        }
    }

    /**
     * Show specified account (unhide)
     */
    fun showAccount(userId: String, address: String) {
        val userHiddenAccounts = hiddenAccountsCache[userId]
        if (userHiddenAccounts != null && userHiddenAccounts.remove(address)) {
            // If the user has no hidden accounts left, remove the user's record
            if (userHiddenAccounts.isEmpty()) {
                hiddenAccountsCache.remove(userId)
            }
            saveHiddenAccounts()
        }
    }

    /**
     * Toggle account visibility
     */
    fun toggleAccountVisibility(userId: String, address: String): Boolean {
        val isHidden = isAccountHidden(userId, address)
        if (isHidden) {
            showAccount(userId, address)
        } else {
            hideAccount(userId, address)
        }
        return !isHidden // Return the hidden state after toggle
    }

    /**
     * Filter out hidden accounts
     */
    fun <T> filterVisibleAccounts(
        userId: String,
        accounts: List<T>,
        addressExtractor: (T) -> String
    ): List<T> {
        val hiddenAddresses = getHiddenAccounts(userId)
        if (hiddenAddresses.isEmpty()) {
            return accounts
        }

        return accounts.filter { account ->
            val address = addressExtractor(account)
            !hiddenAddresses.contains(address)
        }
    }

    /**
     * Clear all hidden accounts for specified user
     */
    fun clearUserHiddenAccounts(userId: String) {
        if (hiddenAccountsCache.remove(userId) != null) {
            saveHiddenAccounts()
        }
    }

    /**
     * Clear all hidden account data
     */
    fun clearAllHiddenAccounts() {
        hiddenAccountsCache.clear()
        sharedPreferences?.edit()?.remove(KEY_HIDDEN_ACCOUNTS)?.apply()
    }

    /**
     * Get hidden account statistics for all users
     */
    fun getHiddenAccountsStats(): Map<String, Int> {
        return hiddenAccountsCache.mapValues { it.value.size }
    }
}
