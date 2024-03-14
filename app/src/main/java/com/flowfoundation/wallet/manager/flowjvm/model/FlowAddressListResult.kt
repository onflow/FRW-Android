package com.flowfoundation.wallet.manager.flowjvm.model


import com.google.gson.annotations.SerializedName

data class FlowAddressListResult(
    @SerializedName("type")
    val type: String,
    @SerializedName("value")
    val value: List<Value>
) {
    data class Value(
        @SerializedName("type")
        val type: String,
        @SerializedName("value")
        val value: String
    )
}