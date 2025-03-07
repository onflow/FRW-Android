package com.flowfoundation.wallet.manager.walletconnect.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken

enum class ResponseStatus(val value: String) {
    PENDING("PENDING"),
    APPROVED("APPROVED"),
}

data class PollingResponse(
    @SerializedName("status")
    val status: ResponseStatus,
    @SerializedName("data")
    val data: PollingData? = null,
    @SerializedName("updates")
    val updates: Service? = null,
    @SerializedName("local")
    val local: Any? = null,
    @SerializedName("reason")
    val reason: String? = null,
    @SerializedName("compositeSignature")
    val compositeSignature: PollingData? = null,
    @SerializedName("authorizationUpdates")
    var authorizationUpdates: Service?=null,
) {

    fun local(): Service? {
        local ?: return null
        val json = Gson().toJson(local)

        return try {
            if (local is ArrayList<*>) {
                Gson().fromJson<List<Service>>(json, object : TypeToken<List<Service>>() {}.type).first()
            } else {
                Gson().fromJson(json, Service::class.java)
            }
        } catch (e: Exception) {
            null
        }
    }
}

data class PollingData(
    @SerializedName("addr")
    val address: String? = null,
    @SerializedName("services")
    val services: List<Service>? = null,
    @SerializedName("f_type")
    val fType: String? = null,
    @SerializedName("f_vsn")
    val fVsn: String? = null,
    @SerializedName("proposer")
    val proposer: Service? = null,
    @SerializedName("payer")
    val payer: List<Service>? = null,
    @SerializedName("authorization")
    val authorization: List<Service>? = null,
    @SerializedName("signature")
    val signature: String? = null,
    @SerializedName("keyId")
    val keyId: Int? = null,
)

data class Service(
    @SerializedName("f_type")
    val fType: String? = null,
    @SerializedName("f_vsn")
    val fVsn: String? = null,
    @SerializedName("type")
    val type: String? = null,
    @SerializedName("method")
    val method: String? = null,
    @SerializedName("endpoint")
    val endpoint: String? = null,
    @SerializedName("params")
    val params: Map<String, String>? = null,
    @SerializedName("uid")
    val uid: String? = null,
    @SerializedName("id")
    val id: String? = null,
    @SerializedName("identity")
    val identity: Identity? = null,
    @SerializedName("provider")
    val provider: Provider? = null,
    @SerializedName("data")
    val data: FCLDataResponse? = null,
)

class Identity(
    @SerializedName("address")
    val address: String,
    @SerializedName("keyId")
    val keyId: Int?,
)

class Provider(
    @SerializedName("address")
    val address: String,
    @SerializedName("name")
    val name: String,
)

class FCLDataResponse(
    @SerializedName("nonce")
    val nonce: String?,
    @SerializedName("address")
    val address: String?,
) {
    class Signature(
        @SerializedName("addr")
        val address: String?,
        @SerializedName("signature")
        val signature: String?,
        @SerializedName("keyId")
        val keyId: Int?,
    )
}