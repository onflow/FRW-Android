package com.flowfoundation.wallet.widgets.webview.fcl.model


import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

data class FclAuthnResponse(
    @SerializedName("body")
    val body: Body,
    @SerializedName("service")
    val service: Service,
    @SerializedName("config")
    val config: FclResponseConfig?,
    @SerializedName("type")
    val type: String,
) : FclResponse {

    data class Body(
        @SerializedName("extensions")
        val extensions: List<Extension>? = null,
        @SerializedName("timestamp")
        val timestamp: Long? = 0,
        @SerializedName("appIdentifier")
        val appIdentifier: String? = null,
        @SerializedName("nonce")
        val nonce: String? = null,
    ) {

        data class Extension(
            @SerializedName("endpoint")
            val endpoint: String?,
            @SerializedName("f_type")
            val fType: String?,
            @SerializedName("f_vsn")
            val fVsn: String?,
            @SerializedName("id")
            val id: String?,
            @SerializedName("identity")
            val identity: Identity?,
            @SerializedName("method")
            val method: String?,
            @SerializedName("provider")
            val provider: Provider?,
            @SerializedName("type")
            val type: String?,
            @SerializedName("uid")
            val uid: String?,
        ) {
            data class Identity(
                @SerializedName("address")
                val address: String
            )

            data class Provider(
                @SerializedName("address")
                val address: String,
                @SerializedName("description")
                val description: String,
                @SerializedName("icon")
                val icon: String,
                @SerializedName("name")
                val name: String
            )
        }
    }

    @Parcelize
    data class Service(
        @SerializedName("type")
        val type: String
    ) : Parcelable

    override fun uniqueId(): String = "${service.type}-$type"
}