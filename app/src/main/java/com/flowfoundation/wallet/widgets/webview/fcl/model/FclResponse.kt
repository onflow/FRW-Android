package com.flowfoundation.wallet.widgets.webview.fcl.model

import com.google.gson.annotations.SerializedName

interface FclResponse {
    fun uniqueId(): String
}

data class FclResponseConfig(
    @SerializedName("app")
    val app: App?,
    @SerializedName("client")
    val client: Client?,
    @SerializedName("services")
    val services: Services?
) {
    data class App(
        @SerializedName("icon")
        val icon: String?,
        @SerializedName("title")
        val title: String?,
    )

    data class Client(
        @SerializedName("extensions")
        val extensions: List<Extension>?,
        @SerializedName("fclLibrary")
        val fclLibrary: String?,
        @SerializedName("fclVersion")
        val fclVersion: String?,
        @SerializedName("hostname")
        val hostname: String?,
        @SerializedName("network")
        val network: String?,
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
                val address: String?,
            )

            data class Provider(
                @SerializedName("address")
                val address: String?,
                @SerializedName("description")
                val description: String?,
                @SerializedName("icon")
                val icon: String?,
                @SerializedName("name")
                val name: String?,
            )
        }
    }

    data class Services(
        @SerializedName("OpenID.scopes")
        val openIDScopes: String?,
    )
}