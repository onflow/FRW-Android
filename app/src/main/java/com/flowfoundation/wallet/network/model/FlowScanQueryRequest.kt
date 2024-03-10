package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName

class FlowScanQueryRequest(
    @SerializedName("query")
    val query: String,
)