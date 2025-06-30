package com.flowfoundation.wallet.manager.flowjvm.transaction

import com.google.gson.annotations.SerializedName
import org.onflow.flow.models.Transaction

data class PayerSignable(
    @SerializedName("message")
    var message: Message? = null,
    @SerializedName("transaction")
    val transaction: Transaction
) {
    data class Message(
        @SerializedName("envelope_message")
        val envelopeMessage: String
    )
}