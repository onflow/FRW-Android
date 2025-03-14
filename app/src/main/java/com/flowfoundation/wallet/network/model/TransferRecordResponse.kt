package com.flowfoundation.wallet.network.model


import com.flowfoundation.wallet.utils.svgToPng
import com.google.gson.annotations.SerializedName

data class TransferRecordResponse(
    @SerializedName("data")
    val data: Data?,
    @SerializedName("message")
    val message: String?,
    @SerializedName("status")
    val status: Int?
) {
    data class Data(
        @SerializedName("next")
        val next: Boolean?,
        @SerializedName("string")
        val string: String?,
        @SerializedName("total")
        val total: Int?,
        @SerializedName("transactions")
        val transactions: List<TransferRecord>?
    )
}

data class EVMTransferRecordResponse(
    @SerializedName("trxs")
    val trxs: List<TransferRecord>?,
    @SerializedName("status")
    val status: Int?
)

data class TransferCountResponse(
    @SerializedName("data")
    val data: Data?,
    @SerializedName("message")
    val message: String?,
    @SerializedName("status")
    val status: Int?
) {
    data class Data(
        @SerializedName("data")
        val data: ParticipationData?,
    ) {
        data class ParticipationData(
            @SerializedName("participations_aggregate")
            val participationAggregate: ParticipationAggregate?
        ) {
            data class ParticipationAggregate(
                @SerializedName("aggregate")
                val aggregate: Aggregate?
            ) {
                data class Aggregate(
                    @SerializedName("count")
                    val count: Int
                )
            }
        }
    }
}

data class TransferRecord(
    @SerializedName("additional_message")
    val additionalMessage: String?,
    @SerializedName("amount")
    val amount: String?,
    @SerializedName("error")
    val error: Boolean?,
    @SerializedName("image")
    val image: String?,
    @SerializedName("receiver")
    val receiver: String?,
    @SerializedName("sender")
    val sender: String?,
    @SerializedName("status")
    val status: String?,
    @SerializedName("time")
    val time: String?,
    @SerializedName("title")
    val title: String?,
    @SerializedName("token")
    val token: String?,
    @SerializedName("transfer_type")
    val transferType: Int?,
    @SerializedName("txid")
    val txid: String?,
    @SerializedName("type")
    val type: Int?,
    @SerializedName("method")
    val method: String?,
    @SerializedName("contractAddr")
    val contractAddr: String?,
) {
    companion object {
        const val TRANSFER_TYPE_SEND = 1
        const val TRANSFER_TYPE_RECEIVE = 2
    }

    fun logo(): String {
        if (image == null) {
            return ""
        }
        return if (image.endsWith(".svg")) {
            image.svgToPng()
        } else {
            image
        }
    }
}

data class TransferRecordList(
    @SerializedName("list")
    val list: List<TransferRecord>,
)