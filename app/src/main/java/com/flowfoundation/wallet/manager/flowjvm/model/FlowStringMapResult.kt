package com.flowfoundation.wallet.manager.flowjvm.model

import com.google.gson.annotations.SerializedName


data class FlowStringMapResult(
    @SerializedName("type")
    val type: String?,
    @SerializedName("value")
    val value: List<Item>?
) {
    data class Item(
        @SerializedName("key")
        val key: Value?,
        @SerializedName("value")
        val value: Value?
    ) {
        data class Value(
            @SerializedName("type")
            val type: String?,
            @SerializedName("value")
            val value: String?
        )
    }
}
