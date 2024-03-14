package com.flowfoundation.wallet.network.flowscan.model

import com.google.gson.annotations.SerializedName

data class FlowScanTransaction(
    @SerializedName("authorizers")
    val authorizers: List<FlowScanAccount>? = null,
    @SerializedName("contractInteractions")
    val contractInteractions: List<ContractInteraction?>? = null,
    @SerializedName("error")
    val error: String?,
    @SerializedName("eventCount")
    val eventCount: Int? = 0,
    @SerializedName("hash")
    val hash: String?,
    @SerializedName("index")
    val index: Int? = 0,
    @SerializedName("payer")
    val payer: FlowScanAccount? = null,
    @SerializedName("proposer")
    val proposer: FlowScanAccount? = null,
    @SerializedName("status")
    val status: String?,
    @SerializedName("time")
    val time: String?
) {
    data class ContractInteraction(
        @SerializedName("identifier")
        val identifier: String?
    )
}

data class FlowScanAccount(
    @SerializedName("address")
    val address: String?
)