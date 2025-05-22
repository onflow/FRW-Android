package com.flowfoundation.wallet.manager.childaccount

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.cache.CacheManager
import com.flowfoundation.wallet.cache.cacheFile
import com.flowfoundation.wallet.manager.flowjvm.CadenceScript
import com.flowfoundation.wallet.manager.flowjvm.executeCadence
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import kotlinx.parcelize.Parcelize
import org.onflow.flow.infrastructure.Cadence
import java.lang.ref.WeakReference


private val TAG = ChildAccountList::class.java.simpleName

class ChildAccountList(
    val address: String,
) {
    private val accountList = mutableListOf<ChildAccount>()

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
        logd(TAG, "Starting refresh for address: $address")
        ioScope {
            try {
                logd(TAG, "Querying account metadata")
                val accounts = queryAccountMeta(address)
                if (accounts == null) {
                    logd(TAG, "No accounts returned from query")
                    return@ioScope
                }
                logd(TAG, "Found ${accounts.size} accounts")
                
                val oldAccounts = accountList.toList()
                accounts.forEach { account -> 
                    account.pinTime = (oldAccounts.firstOrNull { it.address == account.address }?.pinTime ?: 0)
                }
                
                accountList.clear()
                accountList.addAll(accounts)
                logd(TAG, "Updated account list with ${accountList.size} accounts")
                
                try {
                    cache().cache(ArrayList(accountList))
                    logd(TAG, "Cached account list")
                } catch (e: Exception) {
                    logd(TAG, "Error caching accounts: ${e.message}")
                }
                
                dispatchAccountUpdateListener(address, accountList.toList())
                logd(TAG, "Dispatched account update listener")
            } catch (e: Exception) {
                logd(TAG, "Error during refresh: ${e.message}")
                logd(TAG, "Error stack trace: ${e.stackTraceToString()}")
            }
        }
    }

//    private fun queryAccountList(): List<String> {
//        val result = CADENCE_QUERY_CHILD_ACCOUNT_LIST.executeCadence { arg { address(address) } }
//        return result.parseAddressList()
//    }

    private suspend fun queryAccountMeta(address: String): List<ChildAccount>? {
        logd(TAG, "Executing Cadence script for address: $address")
        try {
            val result = CadenceScript.CADENCE_QUERY_CHILD_ACCOUNT_META.executeCadence { arg { Cadence.address(address) } }
            if (result == null) {
                logd(TAG, "Cadence script returned null")
                return null
            }
            val accounts = result.encode()?.parseAccountMetas()
            logd(TAG, "Parsed ${accounts?.size ?: 0} accounts from Cadence result")
            return accounts
        } catch (e: Exception) {
            logd(TAG, "Error executing Cadence script: ${e.message}")
            logd(TAG, "Error stack trace: ${e.stackTraceToString()}")
            return null
        }
    }

    private fun cache(): CacheManager<List<ChildAccount>> {
        @Suppress("UNCHECKED_CAST")
        return CacheManager(
            "${address}.child_account_list".cacheFile(),
            ArrayList::class.java as Class<List<ChildAccount>>,
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
