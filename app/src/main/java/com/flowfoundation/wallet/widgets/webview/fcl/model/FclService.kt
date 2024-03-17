package com.flowfoundation.wallet.widgets.webview.fcl.model


import com.google.gson.annotations.SerializedName

data class FclService(
    @SerializedName("service")
    val service: Service
) {
    data class Service(
        @SerializedName("endpoint")
        val endpoint: String,
        @SerializedName("f_type")
        val fType: String,
        @SerializedName("f_vsn")
        val fVsn: String,
        @SerializedName("identity")
        val identity: Identity,
        @SerializedName("method")
        val method: String,
        @SerializedName("type")
        val type: String,
        @SerializedName("uid")
        val uid: String
    ) {
        data class Identity(
            @SerializedName("address")
            val address: String,
            @SerializedName("keyId")
            val keyId: Int
        )
    }
}