package com.flowfoundation.wallet.page.profile.subpage.wallet.account

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.childaccount.ChildAccount
import com.flowfoundation.wallet.manager.childaccount.ChildAccountList
import com.flowfoundation.wallet.manager.childaccount.ChildAccountUpdateListenerCallback
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager

class ChildAccountsViewModel : ViewModel(), OnTransactionStateChange, ChildAccountUpdateListenerCallback {

    val accountsLiveData = MutableLiveData<List<ChildAccount>>()

    init {
        TransactionStateManager.addOnTransactionStateChange(this)
        ChildAccountList.addAccountUpdateListener(this)
    }

    fun load() {
        val childAccountList = WalletManager.childAccountList() ?: return
        accountsLiveData.postValue(childAccountList.get().map { it.copy() }.sortedByDescending { it.pinTime })
    }

    fun togglePinAccount(account: ChildAccount) {
        WalletManager.childAccountList()?.togglePin(account)
        load()
    }

    override fun onTransactionStateChange() {
        val state = TransactionStateManager.getLastVisibleTransaction() ?: return
        if (state.isSuccess()) {
            WalletManager.refreshChildAccount()
        }
    }

    override fun onChildAccountUpdate(parentAddress: String, accounts: List<ChildAccount>) {
        load()
    }
}