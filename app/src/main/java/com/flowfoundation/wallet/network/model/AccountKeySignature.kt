package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName
import com.nftco.flow.sdk.HashAlgorithm
import com.nftco.flow.sdk.SignatureAlgorithm


data class AccountKeySignature(
    @SerializedName("hash_algo")
    val hashAlgo: Int = HashAlgorithm.SHA2_256.index,

    @SerializedName("sign_algo")
    val signAlgo: Int = SignatureAlgorithm.ECDSA_P256.index,

    @SerializedName("weight")
    val weight: Int = 500,

    @SerializedName("public_key")
    val publicKey: String,

    @SerializedName("sign_message")
    val signMessage: String,

    @SerializedName("signature")
    val signature: String,
)