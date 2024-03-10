package com.flowfoundation.wallet.widgets.webview.fcl.model


import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.manager.flowjvm.transaction.Voucher

data class FclAuthzResponse(
    @SerializedName("body")
    val body: Body,
    @SerializedName("service")
    val service: Service,
    @SerializedName("config")
    val config: FclResponseConfig?,
    @SerializedName("type")
    val type: String
) : FclResponse {

    data class Body(
        @SerializedName("addr")
        val addr: String,
        @SerializedName("cadence")
        val cadence: String,
        @SerializedName("f_type")
        val fType: String,
        @SerializedName("f_vsn")
        val fVsn: String,
        @SerializedName("keyId")
        val keyId: Int,
        @SerializedName("message")
        val message: String,
        @SerializedName("roles")
        val roles: Roles,
        @SerializedName("voucher")
        val voucher: Voucher
    ) {

        data class Roles(
            @SerializedName("authorizer")
            val authorizer: Boolean,
            @SerializedName("payer")
            val payer: Boolean,
            @SerializedName("proposer")
            val proposer: Boolean
        ) {
            fun value() = "roles=$proposer-$authorizer-$payer"
        }
    }

    data class Service(
        @SerializedName("type")
        val type: String
    )

    override fun uniqueId(): String = "${service.type}-$type-${body.roles.value()}"
}