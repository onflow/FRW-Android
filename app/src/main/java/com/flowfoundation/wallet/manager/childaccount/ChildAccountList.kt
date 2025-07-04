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
        ioScope { accountList.addAll(cache().read()?.accountList.orEmpty()) }
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
        cache().cache(ChildAccountCache(accountList.toList()))
    }

    fun refresh() {
        ioScope {
            val accounts = queryAccountMeta(address) ?: return@ioScope
            val oldAccounts = accountList.toList()
            accounts.forEach { account -> account.pinTime = (oldAccounts.firstOrNull { it.address == account.address }?.pinTime ?: 0) }
            accountList.clear()
            accountList.addAll(accounts)
            cache().cache(ChildAccountCache(accountList.toList()))
            dispatchAccountUpdateListener(address, accountList.toList())

            logd(TAG, "refresh: $address, ${accountList.size}")
        }
    }

//    private fun queryAccountList(): List<String> {
//        val result = CADENCE_QUERY_CHILD_ACCOUNT_LIST.executeCadence { arg { address(address) } }
//        return result.parseAddressList()
//    }

    private suspend fun queryAccountMeta(address: String): List<ChildAccount>? {
        val result = CadenceScript.CADENCE_QUERY_CHILD_ACCOUNT_META.executeCadence { arg { Cadence.address(address) } }
        return result?.encode()?.parseAccountMetas()
    }

    private fun cache(): CacheManager<ChildAccountCache> {
        return CacheManager(
            "${address}.child_accounts".cacheFile(),
            ChildAccountCache::class.java,
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

data class ChildAccountCache(
    @SerializedName("accountList")
    val accountList: List<ChildAccount> = emptyList(),
)