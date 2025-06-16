package com.flowfoundation.wallet.manager.childaccount

import android.os.Parcelable
import com.flowfoundation.wallet.R
import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.cache.CacheManager
import com.flowfoundation.wallet.cache.cacheFile
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import kotlinx.parcelize.Parcelize
import java.lang.ref.WeakReference
import com.google.gson.reflect.TypeToken

private val TAG = ChildAccountList::class.java.simpleName

class ChildAccountList(
    val address: String,
) {
    private val accountList = mutableListOf<ChildAccount>()
    private var isRefreshing = false
    private val refreshLock = Any()

    init {
        logd(TAG, "Initializing ChildAccountList for address: $address")
        ioScope { 
            try {
                val cachedAccounts = cache().read().orEmpty()
                logd(TAG, "Loaded ${cachedAccounts.size} accounts from cache")
                accountList.addAll(cachedAccounts)
            } catch (e: Exception) {
                logd(TAG, "Error loading cached accounts: ${e.message}")
            }
        }
        refresh()
    }

    fun get() = accountList.toList()

    fun togglePin(account: ChildAccount) {
        val localAccount = accountList.toList().firstOrNull { it.address == account.address } ?: return
        if (localAccount.pinTime > 0) {
            localAccount.pinTime = 0
        } else {
            localAccount.pinTime = System.currentTimeMillis()
        }
        cache().cache(ArrayList(accountList))
    }

    fun refresh() {
        synchronized(refreshLock) {
            if (isRefreshing) {
                return
            }
            isRefreshing = true
        }

        ioScope {
            try {
                val accounts = queryAccountMeta(address)
                if (accounts == null) {
                    logd(TAG, "No accounts returned from query")
                    return@ioScope
                }

                val oldAccounts = accountList.toList()
                accounts.forEach { account -> 
                    account.pinTime = (oldAccounts.firstOrNull { it.address == account.address }?.pinTime ?: 0)
                }
                
                if (accounts.isNotEmpty()) {
                    accountList.clear()
                    accountList.addAll(accounts)

                    try {
                        cache().cache(ArrayList(accountList))
                    } catch (e: Exception) {
                        logd(TAG, "Error caching accounts: ${e.message}")
                    }
                } else {
                    logd(TAG, "No accounts to update, keeping existing list")
                }
                
                dispatchAccountUpdateListener(address, accountList.toList())
            } catch (e: Exception) {
                logd(TAG, "Error during refresh: ${e.message}")
            } finally {
                synchronized(refreshLock) {
                    isRefreshing = false
                }
            }
        }
    }

    private suspend fun queryAccountMeta(address: String): List<ChildAccount>? {
        try {
            val wallet = WalletManager.wallet()
            val account = wallet?.accounts?.values?.flatten()?.firstOrNull { it.address == address }
            if (account == null) {
                logd(TAG, "No account found for address: $address")
                return null
            }

            val childAccounts = account.fetchChild()

            return childAccounts.map { childAccount ->
                ChildAccount(
                    address = childAccount.address.base16Value,
                    name = childAccount.name ?: R.string.default_child_account_name.res2String(),
                    icon = childAccount.icon.orEmpty().ifBlank { "https://lilico.app/placeholder-2.0.png" },
                    description = childAccount.description
                )
            }
        } catch (e: Exception) {
            logd(TAG, "Error fetching child accounts: ${e.message}")
            return null
        }
    }

    private fun cache(): CacheManager<List<ChildAccount>> {
        return CacheManager(
            "${address}.child_account_list".cacheFile(),
            object : TypeToken<List<ChildAccount>>() {}.type
        )
    }

    companion object {
        private val accountUpdateListener = mutableListOf<WeakReference<ChildAccountUpdateListenerCallback>>()

        fun addAccountUpdateListener(listener: ChildAccountUpdateListenerCallback) {
            accountUpdateListener.add(WeakReference(listener))
        }

        fun dispatchAccountUpdateListener(parentAddress: String, accounts: List<ChildAccount>) {
            accountUpdateListener.forEach { it.get()?.onChildAccountUpdate(parentAddress, accounts) }
            accountUpdateListener.removeAll { it.get() == null }
        }
    }
}

interface ChildAccountUpdateListenerCallback {
    fun onChildAccountUpdate(parentAddress: String, accounts: List<ChildAccount>)
}

@Parcelize
data class ChildAccount(
    @SerializedName("address")
    val address: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("icon")
    val icon: String,
    @SerializedName("pinTime")
    var pinTime: Long = 0,
    @SerializedName("description")
    val description: String? = null,
) : Parcelable
