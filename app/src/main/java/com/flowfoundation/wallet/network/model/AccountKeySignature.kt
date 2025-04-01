package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm


data class AccountKeySignature(
    @SerializedName("hash_algo")
    val hashAlgo: Int = HashingAlgorithm.SHA2_256.index, //to-do: check index value

    @SerializedName("sign_algo")
    val signAlgo: Int = SigningAlgorithm.ECDSA_P256.index,

    @SerializedName("weight")
    val weight: Int = 500,

    @SerializedName("public_key")
    val publicKey: String,

    @SerializedName("sign_message")
    val signMessage: String,

    @SerializedName("signature")
    val signature: String,
)