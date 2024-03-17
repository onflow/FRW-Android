package com.flowfoundation.wallet.widgets.webview.fcl.model


import com.google.gson.annotations.SerializedName

data class FclSimpleResponse(
    @SerializedName("service")
    val service: Service,
    @SerializedName("type")
    val type: String
) {

    fun serviceType() = service.type

    data class Service(
        @SerializedName("type")
        val type: String
    )
}