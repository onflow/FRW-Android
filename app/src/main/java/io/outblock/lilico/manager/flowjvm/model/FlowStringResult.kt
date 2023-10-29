package io.outblock.lilico.manager.flowjvm.model

import com.google.gson.annotations.SerializedName


data class FlowStringResult(
    @SerializedName("type")
    val type: String,
    @SerializedName("value")
    val value: String
)