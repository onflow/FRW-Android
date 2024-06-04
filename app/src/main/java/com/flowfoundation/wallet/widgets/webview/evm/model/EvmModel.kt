package com.flowfoundation.wallet.widgets.webview.evm.model

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class EvmTransaction(
    @SerializedName("value")
    val value: String?,
    @SerializedName("to")
    val to: String?,
    @SerializedName("gas")
    val gas: String?,
    @SerializedName("data")
    val data: String?,
    @SerializedName("from")
    val from: String?
)