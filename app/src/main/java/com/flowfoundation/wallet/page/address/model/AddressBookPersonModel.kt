package com.flowfoundation.wallet.page.address.model

import com.flowfoundation.wallet.network.model.AddressBookContact

data class AddressBookPersonModel(
    val data: AddressBookContact,
    var isFriend: Boolean? = null,
)