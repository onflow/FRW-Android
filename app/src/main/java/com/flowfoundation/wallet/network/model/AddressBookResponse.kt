package com.flowfoundation.wallet.network.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.page.address.FlowDomainServer
import kotlinx.parcelize.Parcelize

data class AddressBookResponse(
    @SerializedName("data")
    val data: AddressBookContactBookList,

    @SerializedName("message")
    val message: String,

    @SerializedName("status")
    val status: Int,
)

data class AddressBookContactBookList(
    @SerializedName("contacts")
    var contacts: List<AddressBookContact>?,
)

@Parcelize
data class AddressBookContact(
    @SerializedName("address")
    val address: String? = null,
    @SerializedName("avatar")
    val avatar: String? = null,
    @SerializedName("contact_name")
    val contactName: String? = null,
    @SerializedName("username")
    val username: String? = null,
    @SerializedName("domain")
    val domain: AddressBookDomain? = null,
    @SerializedName("contact_type")
    val contactType: Int? = null,
    @SerializedName("id")
    val id: String? = null,
) : Parcelable {
    fun name(default: String = "-"): String {
        return (if (username.isNullOrEmpty()) contactName else username) ?: default
    }

    fun prefixName(): String {
        val name = name("")
        if (name.isEmpty()) {
            return "0x"
        }
        return name.first().toString().uppercase()
    }

    fun uniqueId() = "${address}-${domain?.domainType}-${name()}-$contactType"

    companion object {
        const val CONTACT_TYPE_USER = 1
        const val CONTACT_TYPE_DOMAIN = 2
    }
}

@Parcelize
data class AddressBookDomain(
    @SerializedName("domain_type")
    val domainType: Int,
    @SerializedName("value")
    val value: String? = null,
) : Parcelable {
    companion object {
        val DOMAIN_FIND_XYZ = FlowDomainServer.FIND.type
    }
}