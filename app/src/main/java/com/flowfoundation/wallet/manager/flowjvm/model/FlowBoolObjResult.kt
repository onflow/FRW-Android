package com.flowfoundation.wallet.manager.flowjvm.model


import com.google.gson.annotations.SerializedName

data class FlowBoolObjResult(
    @SerializedName("type")
    val type: String,
    @SerializedName("value")
    val value: Value
) {
    data class Value(
        @SerializedName("type")
        val type: String,
        @SerializedName("value")
        val value: Boolean
    )
}