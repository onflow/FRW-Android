package com.flowfoundation.wallet.network.model


import com.google.gson.annotations.SerializedName

data class ClaimDomainPrepareResponse(
    @SerializedName("data")
    val data: ClaimDomainPrepare?,
    @SerializedName("message")
    val message: String?,
    @SerializedName("status")
    val status: Int?
)

data class ClaimDomainPrepare(
    @SerializedName("cadence")
    val cadence: String?,
    @SerializedName("domain")
    val domain: String?,
    @SerializedName("flowns_server_address")
    val flownsServerAddress: String?,
    @SerializedName("lilico_server_address")
    val lilicoServerAddress: String?
)