package com.flowfoundation.wallet.page.address.model

import com.flowfoundation.wallet.network.model.AddressBookContact
import com.google.gson.annotations.SerializedName

data class AddressBookPersonModel(
    @SerializedName("data")
    val data: AddressBookContact,
    @SerializedName("isFriend")
    var isFriend: Boolean? = null,
)