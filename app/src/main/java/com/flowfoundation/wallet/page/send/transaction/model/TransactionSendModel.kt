package com.flowfoundation.wallet.page.send.transaction.model

import com.flowfoundation.wallet.network.model.AddressBookContact

class TransactionSendModel(
    val qrcode: String? = null,
    val selectedAddress: AddressBookContact? = null,
    val isClearInputFocus: Boolean? = null,
)