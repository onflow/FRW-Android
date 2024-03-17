package com.flowfoundation.wallet.page.address

import android.annotation.SuppressLint
import androidx.recyclerview.widget.DiffUtil
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryAddressByDomainFind
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryAddressByDomainFlowns
import com.flowfoundation.wallet.network.model.AddressBookContact
import com.flowfoundation.wallet.network.model.AddressBookDomain
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.page.address.model.AddressBookPersonModel

enum class FlowDomainServer(
    val server: String,
    val domain: String,
    val type: Int,
) {
    FIND("find", "find", 1),
    FLOWNS("flowns", "fn", 2),
    MEOW("meow", "meow", 3),
}

val addressBookDiffCallback = object : DiffUtil.ItemCallback<Any>() {
    override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
        if (oldItem is AddressBookPersonModel && newItem is AddressBookPersonModel) {
            return oldItem.data == newItem.data
        }
        return oldItem == newItem
    }

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
        if (oldItem is AddressBookPersonModel && newItem is AddressBookPersonModel) {
            return oldItem.data == newItem.data && oldItem.isFriend == newItem.isFriend
        }
        return oldItem == newItem
    }
}

fun UserInfoData.toContact(): AddressBookContact {
    return AddressBookContact(
        contactName = nickname,
        address = address,
        username = username,
        avatar = avatar,
        contactType = AddressBookContact.CONTACT_TYPE_USER,
    )
}

fun isAddressBookAutoSearch(text: CharSequence?): Boolean {
    if (text.isNullOrBlank()) {
        return false
    }
    return !text.trim().contains(" ") && (text.endsWith(".find") || text.endsWith(".fn") || text.endsWith(".meow"))
}

fun List<AddressBookContact>.removeRepeated(): List<AddressBookContact> {
    return distinctBy { it.uniqueId() }
}

fun queryAddressBookFromBlockchain(keyword: String, server: FlowDomainServer): AddressBookContact? {
    val name = keyword.lowercase().removeSuffix(".${server.domain}")
    if (name.contains(".")) {
        return null
    }
    val address = when (server) {
        FlowDomainServer.FIND -> cadenceQueryAddressByDomainFind(name)
        FlowDomainServer.FLOWNS, FlowDomainServer.MEOW -> cadenceQueryAddressByDomainFlowns(name, root = server.domain)
    }

    if (address.isNullOrBlank()) {
        return null
    }

    return AddressBookContact(
        address = address,
        contactName = name,
        domain = AddressBookDomain(domainType = server.type, value = name),
        contactType = AddressBookContact.CONTACT_TYPE_DOMAIN,
    )
}