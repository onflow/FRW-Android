package com.flowfoundation.wallet.page.address

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.cache.addressBookCache
import com.flowfoundation.wallet.cache.recentTransactionCache
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.AddressBookContact
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.page.address.model.AddressBookCharModel
import com.flowfoundation.wallet.page.address.model.AddressBookPersonModel
import com.flowfoundation.wallet.ReactNativeDemoActivity
import com.flowfoundation.wallet.utils.*
import com.flowfoundation.wallet.wallet.toAddress

class AddressBookViewModel : ViewModel() {
    private val addressBookList = mutableListOf<Any>()
    val addressBookLiveData = MutableLiveData<List<Any>>()

    val remoteEmptyLiveData = MutableLiveData<Boolean>()
    val localEmptyLiveData = MutableLiveData<Boolean>()
    val onSearchStartLiveData = MutableLiveData<Boolean>()

    val showProgressLiveData = MutableLiveData<Boolean>()

    val clearEditTextFocusLiveData = MutableLiveData<Boolean>()

    private var searchKeyword: String = ""

    private val isSendPage by lazy { BaseActivity.getCurrentActivity()?.javaClass == ReactNativeDemoActivity::class.java }

    fun loadAddressBook() {
        if (searchKeyword.isNotBlank()) {
            return
        }
        viewModelIOScope(this) {
            val recentList = if (isSendPage) recentTransactionCache().read()?.contacts.orEmpty() else emptyList()

            addressBookCache().read()?.contacts?.removeRepeated()?.let { updateOriginAddressBook(parseAddressBook(merge(recentList, it))) }

            val service = retrofit().create(ApiService::class.java)
            val resp = service.getAddressBook()
            resp.data.contacts?.removeRepeated()?.let {
                updateOriginAddressBook(parseAddressBook(merge(recentList, it)))
                addressBookCache().cache(resp.data)
            }
            showProgressLiveData.postValue(false)
        }
    }

    fun searchKeyword() = searchKeyword

    fun searchLocal(keyword: String) {
        searchKeyword = keyword
        if (keyword.isBlank()) {
            addressBookLiveData.postValue(addressBookList)
            return
        }

        val localData = addressBookList
            .filterIsInstance<AddressBookPersonModel>()
            .map { it.data }
            .filter { it.name().contains(keyword) || it.address?.contains(keyword) ?: false }
        addressBookLiveData.postValue(parseAddressBook(localData))
        if (localData.isEmpty()) {
            localEmptyLiveData.postValue(true)
        }
    }

    fun searchRemote(keyword: String, includeLocal: Boolean = false, isAutoSearch: Boolean = false) {
        searchKeyword = keyword
        if (keyword.isBlank()) {
            addressBookLiveData.postValue(addressBookList)
            return
        }
        onSearchStartLiveData.postValue(true)
        viewModelIOScope(this) {
            val data = mutableListOf<Any>()

            if (includeLocal) {
                val localData = addressBookList
                    .filterIsInstance<AddressBookPersonModel>()
                    .filter { it.data.name().contains(keyword) || it.data.address?.contains(keyword) ?: false }
                data.addAll(localData)
                addressBookLiveData.postValue(data)
            }

            if (!isAutoSearch) {
                searchUsers(keyword, data)
            }
            queryOnBlockChain(keyword, data, FlowDomainServer.FLOWNS)
            queryOnBlockChain(keyword, data, FlowDomainServer.FIND)
            queryOnBlockChain(keyword, data, FlowDomainServer.MEOW)

            if (data.isEmpty()) {
                remoteEmptyLiveData.postValue(true)
            }
        }
    }

    fun clearSearch() {
        loadAddressBook()
    }

    fun delete(contact: AddressBookContact) {
        showProgressLiveData.postValue(true)
        viewModelIOScope(this) {
            val service = retrofit().create(ApiService::class.java)
            service.deleteAddressBook(contact.id.orEmpty())
            loadAddressBook()
            showProgressLiveData.postValue(false)
            val data = addressBookLiveData.value?.toMutableList() ?: return@viewModelIOScope
            addressBookList.removeIf { it is AddressBookPersonModel && it.data == contact }
            data.removeIf { it is AddressBookPersonModel && it.data == contact }
            addressBookLiveData.postValue(data)
        }
    }

    fun isAddressBookContains(contact: AddressBookPersonModel): Boolean {
        return addressBookList.filterIsInstance<AddressBookPersonModel>().firstOrNull { it.data.uniqueId() == contact.data.uniqueId() } != null
    }

    fun addFriend(data: AddressBookContact) {
        showProgressLiveData.postValue(true)
        viewModelIOScope(this) {
            val service = retrofit().create(ApiService::class.java)
            try {
                val resp = if (data.contactType == AddressBookContact.CONTACT_TYPE_USER) {
                    service.addAddressBook(
                        mapOf(
                            "contact_name" to data.contactName,
                            "address" to data.address?.toAddress(),
                            "domain" to data.domain?.value.orEmpty(),
                            "domain_type" to (data.domain?.domainType ?: 0),
                            "username" to data.username,
                        )
                    )
                } else {
                    service.addAddressBookExternal(
                        mapOf(
                            "contact_name" to data.name(),
                            "address" to data.address?.toAddress(),
                            "domain" to data.domain?.value.orEmpty(),
                            "domain_type" to (data.domain?.domainType ?: 0),
                            "username" to data.username,
                        )
                    )
                }

                if (resp.status == 200) {
                    addressBookList.add(AddressBookPersonModel(data = data))

                    val list = (addressBookLiveData.value ?: emptyList()).toMutableList()
                    val index = list.indexOfFirst { it is AddressBookPersonModel && it.data.uniqueId() == data.uniqueId() }
                    if (index >= 0) {
                        list[index] = AddressBookPersonModel(data = data, isFriend = true)
                    }
                    addressBookLiveData.postValue(list)
                    toast(msgRes = R.string.address_add_success)
                } else {
                    toast(msgRes = R.string.address_add_failed)
                }
            } catch (e: Exception) {
                loge(e)
                toast(msgRes = R.string.address_add_failed)
            }

            showProgressLiveData.postValue(false)
        }
    }

    fun clearInputFocus() = clearEditTextFocusLiveData.postValue(true)

    private fun updateOriginAddressBook(data: List<Any>) {
        addressBookList.clear()
        addressBookList.addAll(data)
        addressBookLiveData.postValue(data)
    }

    private suspend fun searchUsers(keyword: String, data: MutableList<Any>) {
        try {
            val service = retrofit().create(ApiService::class.java)
            val resp = service.searchUser(keyword)
            logd("searchUsers", "resp:$resp")
            resp.data.users?.let { users ->
                if (users.isEmpty()) return@let
                data.add(AddressBookCharModel(text = "Lilico user"))
                data.addAll(users.map { AddressBookPersonModel(data = it.toContact()) })
            }
            if (keyword == searchKeyword) {
                addressBookLiveData.postValue(data)
            }
        } catch (e: Exception) {
            loge(e)
        }
    }

    private fun queryOnBlockChain(keyword: String, data: MutableList<Any>, server: FlowDomainServer) {
        safeRun {
            ioScope {
                val contact = queryAddressBookFromBlockchain(keyword, server) ?: return@ioScope

                if (contact.isContactInBookList()) {
                    return@ioScope
                }

                data.add(AddressBookCharModel(text = ".${server.server}"))
                data.add(AddressBookPersonModel(data = contact))
                if (keyword == searchKeyword) {
                    addressBookLiveData.postValue(data)
                }
            }
        }
    }

    private fun merge(recentList: List<AddressBookContact>, addressBookList: List<AddressBookContact>): List<AddressBookContact> {
        val recent = recentList.toMutableList()
        recent.removeAll { addressBookList.firstOrNull { bookItem -> bookItem.address == it.address } != null }
        return mutableListOf<AddressBookContact>().apply {
            addAll(recent)
            addAll(addressBookList)
        }
    }

    private fun parseAddressBook(data: List<AddressBookContact>): List<Any> {
        val charList = mutableListOf<String>()
        return data.sortedBy { it.name() }.map {
            mutableListOf<Any>(
                AddressBookPersonModel(it)
            ).apply {
                if (!charList.contains(it.prefixName())) {
                    add(0, AddressBookCharModel(text = "#${it.prefixName()}"))
                    charList.add(it.prefixName())
                }
            }
        }.flatten()
    }

    private fun AddressBookContact.isContactInBookList(): Boolean {
        return addressBookLiveData.value?.filterIsInstance<AddressBookPersonModel>()
            ?.firstOrNull { it.data.address == address && it.data.domain?.domainType == domain?.domainType } != null
    }
}