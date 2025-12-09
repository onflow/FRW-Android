package com.flowfoundation.wallet.page.profile.subpage.wallet.account

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.OnAccountUpdate
import com.flowfoundation.wallet.manager.childaccount.ChildAccount
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.walletdata.WalletDataManager

class ChildAccountsViewModel : ViewModel(), OnTransactionStateChange, OnAccountUpdate {

    val accountsLiveData = MutableLiveData<List<ChildAccount>>()

    init {
        TransactionStateManager.addOnTransactionStateChange(this)
        AccountManager.addListener(this)
    }

    fun load() {
        val childAccountList = WalletManager.childAccountList()
        accountsLiveData.postValue(childAccountList.map { it.copy() }.sortedByDescending { it.pinTime })
    }

    fun togglePinAccount(account: ChildAccount) {
        WalletManager.togglePin(account)
        load()
    }

    override fun onTransactionStateChange() {
        val state = TransactionStateManager.getLastVisibleTransaction() ?: return
        if (state.isSuccess()) {
            WalletDataManager.refreshCurrentAccountChildAccounts()
        }
    }

    override fun onAccountUpdate(account: Account) {
        load()
    }
}
