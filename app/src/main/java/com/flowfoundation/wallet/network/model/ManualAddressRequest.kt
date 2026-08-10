package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName

data class ManualAddressRequest(
    @SerializedName("hashAlgorithm")
    val hashAlgorithm: Int,

    @SerializedName("publicKey")
    val publicKey: String,

    @SerializedName("signatureAlgorithm")
    val signatureAlgorithm: Int,

    @SerializedName("weight")
    val weight: Int
)
