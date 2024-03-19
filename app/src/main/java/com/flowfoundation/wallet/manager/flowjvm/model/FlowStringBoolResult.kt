package com.flowfoundation.wallet.manager.flowjvm.model

import com.google.gson.annotations.SerializedName


data class FlowStringBoolResult(
    @SerializedName("type")
    val type: String?,
    @SerializedName("value")
    val value: List<Item>?
) {
    data class Item(
        @SerializedName("key")
        val key: Value?,
        @SerializedName("value")
        val value: BoolValue?
    ) {
        data class Value(
            @SerializedName("type")
            val type: String?,
            @SerializedName("value")
            val value: String?
        )

        data class BoolValue(
            @SerializedName("type")
            val type: String?,
            @SerializedName("value")
            val value: Boolean? = false
        )
    }
}
