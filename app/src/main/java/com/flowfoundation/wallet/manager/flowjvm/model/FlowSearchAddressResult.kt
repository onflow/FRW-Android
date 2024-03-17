package com.flowfoundation.wallet.manager.flowjvm.model

import com.google.gson.annotations.SerializedName

/**
 * Created by Mengxy on 10/29/23.
 */
data class FlowSearchAddressResult(
    @SerializedName("type")
    val type: String,
    @SerializedName("value")
    val value: Value
) {
    data class Value(
        @SerializedName("type")
        val type: String,
        @SerializedName("value")
        val value: String,
    )
}
