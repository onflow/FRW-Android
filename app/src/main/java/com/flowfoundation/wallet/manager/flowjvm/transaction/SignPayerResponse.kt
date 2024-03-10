package com.flowfoundation.wallet.manager.flowjvm.transaction


import com.google.gson.annotations.SerializedName

data class SignPayerResponse(
    @SerializedName("envelopeSigs")
    val envelopeSigs: EnvelopeSigs
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