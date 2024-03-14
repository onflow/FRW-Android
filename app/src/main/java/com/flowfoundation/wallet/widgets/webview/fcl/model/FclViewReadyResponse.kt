package com.flowfoundation.wallet.widgets.webview.fcl.model


import com.google.gson.annotations.SerializedName

data class FclViewReadyResponse(
    @SerializedName("body")
    val body: Body,
    @SerializedName("config")
    val config: Config,
    @SerializedName("fclVersion")
    val fclVersion: String,
    @SerializedName("service")
    val service: Service,
    @SerializedName("type")
    val type: String
) {

    fun uniqueId() = service.type + body.extensions.first().id

    fun serviceType() = service.type

    data class Body(
        @SerializedName("data")
        val `data`: Any,
        @SerializedName("extensions")
        val extensions: List<Extension>,
        @SerializedName("timestamp")
        val timestamp: Long
    ) {
        data class Extension(
            @SerializedName("endpoint")
            val endpoint: String,
            @SerializedName("f_type")
            val fType: String,
            @SerializedName("f_vsn")
            val fVsn: String,
            @SerializedName("id")
            val id: String,
            @SerializedName("identity")
            val identity: Identity,
            @SerializedName("method")
            val method: String,
            @SerializedName("provider")
            val provider: Provider,
            @SerializedName("type")
            val type: String,
            @SerializedName("uid")
            val uid: String
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

    data class Config(
        @SerializedName("app")
        val app: App,
        @SerializedName("client")
        val client: Client,
        @SerializedName("discoveryAuthnInclude")
        val discoveryAuthnInclude: List<Any>,
        @SerializedName("services")
        val services: Services
    ) {
        class App()

        data class Client(
            @SerializedName("fclLibrary")
            val fclLibrary: String,
            @SerializedName("fclVersion")
            val fclVersion: String,
            @SerializedName("hostname")
            val hostname: String
        )

        class Services
    }

    data class Service(
        @SerializedName("type")
        val type: String
    )
}