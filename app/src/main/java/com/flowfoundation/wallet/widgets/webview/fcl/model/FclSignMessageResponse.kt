package com.flowfoundation.wallet.widgets.webview.fcl.model


import com.google.gson.annotations.SerializedName

data class FclSignMessageResponse(
    @SerializedName("body")
    val body: Body?,
    @SerializedName("config")
    val config: FclResponseConfig?,
    @SerializedName("fclVersion")
    val fclVersion: String,
    @SerializedName("service")
    val service: Service,
    @SerializedName("type")
    val type: String,
) : FclResponse {
    data class Body(
        @SerializedName("message")
        val message: String?,
    )

    data class Service(
        @SerializedName("type")
        val type: String?,
    )

    override fun uniqueId(): String = "${service.type}-$type-${body?.message}"
}