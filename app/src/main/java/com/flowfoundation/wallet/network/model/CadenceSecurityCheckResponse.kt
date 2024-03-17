package com.flowfoundation.wallet.network.model


import com.google.gson.annotations.SerializedName

data class CadenceSecurityCheckResponse(
    @SerializedName("data")
    val data: Data?,
    @SerializedName("message")
    val message: String?,
    @SerializedName("status")
    val status: Int?
) {
    data class Data(
        @SerializedName("auditors")
        val auditors: List<Auditor?>?,
        @SerializedName("template")
        val template: Template?
    ) {
        data class Auditor(
            @SerializedName("address")
            val address: String?,
            @SerializedName("f_type")
            val fType: String?,
            @SerializedName("f_version")
            val fVersion: String?,
            @SerializedName("name")
            val name: String?,
            @SerializedName("twitter_url")
            val twitterUrl: String?,
            @SerializedName("website_url")
            val websiteUrl: String?
        )

        data class Template(
            @SerializedName("data")
            val data: Data?,
            @SerializedName("f_type")
            val fType: String?,
            @SerializedName("f_version")
            val fVersion: String?,
            @SerializedName("id")
            val id: String?
        ) {
            data class Data(
                @SerializedName("arguments")
                val arguments: Arguments?,
                @SerializedName("cadence")
                val cadence: String?,
                @SerializedName("dependencies")
                val dependencies: Map<String, Map<String, DependenciesToken>>?,
                @SerializedName("interface")
                val interfaceX: String?,
                @SerializedName("messages")
                val messages: Messages?,
                @SerializedName("type")
                val type: String?
            ) {
                class Arguments

                data class DependenciesToken(
                    @SerializedName("mainnet")
                    val mainnet: Mainnet?,
                    @SerializedName("testnet")
                    val testnet: Testnet?
                ) {
                    data class Mainnet(
                        @SerializedName("address")
                        val address: String?,
                        @SerializedName("contract")
                        val contract: String?,
                        @SerializedName("fq_address")
                        val fqAddress: String?,
                        @SerializedName("pin")
                        val pin: String?,
                        @SerializedName("pin_block_height")
                        val pinBlockHeight: Int?
                    )

                    data class Testnet(
                        @SerializedName("address")
                        val address: String?,
                        @SerializedName("contract")
                        val contract: String?,
                        @SerializedName("fq_address")
                        val fqAddress: String?,
                        @SerializedName("pin")
                        val pin: String?,
                        @SerializedName("pin_block_height")
                        val pinBlockHeight: Int?
                    )
                }

                data class Messages(
                    @SerializedName("description")
                    val description: Description?,
                    @SerializedName("title")
                    val title: Title?
                ) {
                    data class Description(
                        @SerializedName("i18n")
                        val i18n: Map<String, String>?,
                    )

                    data class Title(
                        @SerializedName("i18n")
                        val i18n: Map<String, String>?
                    )
                }
            }
        }
    }
}