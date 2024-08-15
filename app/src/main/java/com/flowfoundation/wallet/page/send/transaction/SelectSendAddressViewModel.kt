package com.flowfoundation.wallet.page.send.transaction

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.cache.addressBookCache
import com.flowfoundation.wallet.cache.recentTransactionCache
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.AddressBookContact
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.page.address.model.AddressBookAccountModel
import com.flowfoundation.wallet.page.address.model.AddressBookPersonModel
import com.flowfoundation.wallet.page.address.model.AddressBookTitleModel
import com.flowfoundation.wallet.page.address.removeRepeated
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.viewModelIOScope

class SelectSendAddressViewModel : ViewModel() {

    val recentListLiveData = MutableLiveData<List<Any>>()
    val addressListLiveData = MutableLiveData<List<Any>>()
    val accountListLiveData = MutableLiveData<List<Any>>()

    val onAddressSelectedLiveData = MutableLiveData<AddressBookContact?>()

    fun load(type: Int) {
        when (type) {
            AddressPageFragment.TYPE_RECENT -> loadRecent()
            AddressPageFragment.TYPE_ADDRESS -> loadAddressBook()
            AddressPageFragment.TYPE_ACCOUNT -> loadAccounts()
        }
    }

    private fun loadRecent() {
        viewModelIOScope(this) {
            recentTransactionCache().read()?.let { data ->
                recentListLiveData.postValue(data.contacts?.map { AddressBookPersonModel(data = it) }.orEmpty())
            }
        }
    }

    private fun loadAddressBook() {
        viewModelIOScope(this) {
            addressBookCache().read()?.contacts?.removeRepeated()?.let { data -> addressListLiveData.postValue(data.format()) }

            val service = retrofit().create(ApiService::class.java)
            val resp = service.getAddressBook()
            val contacts = resp.data.contacts?.removeRepeated() ?: return@viewModelIOScope
            addressListLiveData.postValue(contacts.format())
            resp.data.contacts = contacts
            addressBookCache().cache(resp.data)
        }
    }

    private fun loadAccounts() {
        viewModelIOScope(this) {
            val parentAddress = WalletManager.wallet()?.walletAddress() ?: return@viewModelIOScope
            val accountList = mutableListOf<Any>()
            accountList.add(AddressBookAccountModel(parentAddress))
            val linkedAccounts = WalletManager.childAccountList(parentAddress)?.get()?.map{
                    child ->
                AddressBookAccountModel(child.address)
            }?.toMutableList() ?: mutableListOf()
            val evmAddress = EVMWalletManager.getEVMAddress().orEmpty()
            if (evmAddress.isNotEmpty()) {
                linkedAccounts.add(0, AddressBookAccountModel(evmAddress))
            }
            if (linkedAccounts.isNotEmpty()) {
                accountList.add(AddressBookTitleModel(R.string.linked_account.res2String()))
                accountList.addAll(linkedAccounts)
            }
            accountListLiveData.postValue(accountList)
        }
    }

    private fun List<AddressBookContact>.format() = this.sortedBy { it.name() }.map { AddressBookPersonModel(data = it) }
}