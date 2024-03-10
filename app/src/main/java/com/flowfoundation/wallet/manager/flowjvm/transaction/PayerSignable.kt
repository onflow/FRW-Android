package com.flowfoundation.wallet.manager.flowjvm.transaction


import com.google.gson.annotations.SerializedName

data class PayerSignable(
    @SerializedName("message")
    var message: Message? = null,
    @SerializedName("transaction")
    val transaction: Voucher
) {
    data class Message(
        @SerializedName("envelope_message")
        val envelopeMessage: String
    )
}