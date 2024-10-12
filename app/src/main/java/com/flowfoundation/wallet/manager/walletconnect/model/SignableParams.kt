package com.flowfoundation.wallet.manager.walletconnect.model

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable


@Serializable
data class SignableParams(
    @SerializedName("addr")
    val addr: String? = null,
    @SerializedName("address")
    val address: String? = null,
    @SerializedName("nonce")
    val nonce: String? = null,
    @SerializedName("appIdentifier")
    val appIdentifier: String? = null,
)
