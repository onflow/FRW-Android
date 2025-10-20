package com.flowfoundation.wallet.manager.flowjvm.transaction


import com.google.gson.annotations.SerializedName

data class SignPayerResponse(
    @SerializedName("data")
    val data: EnvelopeSigs
) {
    data class EnvelopeSigs(
        @SerializedName("address")
        val address: String,
        @SerializedName("keyId")
        val keyId: Int,
        @SerializedName("sig")
        val sig: String
    )
}
