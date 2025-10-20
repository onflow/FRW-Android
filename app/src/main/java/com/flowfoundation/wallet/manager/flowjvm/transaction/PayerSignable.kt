package com.flowfoundation.wallet.manager.flowjvm.transaction

import com.google.gson.annotations.SerializedName
import org.onflow.flow.models.Transaction

data class PayerSignable(
    @SerializedName("message")
    var message: Message? = null,
    @SerializedName("network")
    val network: String,
    @SerializedName("transaction")
    val transaction: Transaction? = null
) {
    data class Message(
        @SerializedName("envelope_message")
        val envelopeMessage: String
    )
}

/**
 * Data class for /api/signAsFeePayer API request
 * Format: { "message": { "envelopeMessage": "string" }, "network": "mainnet" }
 */
data class FeePayerSignRequest(
    @SerializedName("message")
    val message: FeePayerMessage,
    @SerializedName("network")
    val network: String
) {
    data class FeePayerMessage(
        @SerializedName("envelopeMessage")
        val envelopeMessage: String
    )
}

/**
 * Data class for /api/signAsBridgePayer API request  
 * Format: { "message": { "payload": "string" }, "network": "mainnet" }
 */
data class BridgePayerSignRequest(
    @SerializedName("message")
    val message: BridgePayerMessage,
    @SerializedName("network")
    val network: String
) {
    data class BridgePayerMessage(
        @SerializedName("payload")
        val payload: String
    )
}