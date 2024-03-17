package com.flowfoundation.wallet.network.model


import com.google.gson.annotations.SerializedName

data class ClaimDomainSignatureResponse(
    @SerializedName("data")
    val data: Data?,
    @SerializedName("message")
    val message: String?,
    @SerializedName("status")
    val status: Int?
) {
    data class Data(
        @SerializedName("txId")
        val txId: String?
    )
}