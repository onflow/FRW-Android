package io.outblock.lilico.manager.flowjvm.model

import com.google.gson.annotations.SerializedName

/**
 * Created by Mengxy on 10/29/23.
 */
data class FlowBoolResult(
    @SerializedName("type")
    val type: String,
    @SerializedName("value")
    val value: Boolean? = false
)
