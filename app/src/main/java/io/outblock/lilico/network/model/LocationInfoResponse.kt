package io.outblock.lilico.network.model

import com.google.gson.annotations.SerializedName
import io.outblock.lilico.page.profile.subpage.wallet.device.model.DeviceModel

data class LocationInfoResponse(
    @SerializedName("data")
    val data: LocationInfo?,
    @SerializedName("message")
    val message: String?,
    @SerializedName("status")
    val status: Int?
)

data class DeviceListResponse(
    @SerializedName("data")
    val data: List<DeviceModel>?,
    @SerializedName("message")
    val message: String?,
    @SerializedName("status")
    val status: Int?
)

data class KeyDeviceInfoResponse(
    @SerializedName("data")
    val data: Data,
    @SerializedName("message")
    val message: String?,
    @SerializedName("status")
    val status: Int?
) {
    data class Data(
        @SerializedName("result")
        val result: List<KeyDeviceInfo>?,
    )
}

data class KeyDeviceInfo(
    @SerializedName("device")
    val device: DeviceModel?,
    @SerializedName("pubkey")
    val pubKey: KeyModel?
) {
    data class KeyModel(
        @SerializedName("hash_algo")
        val hashAlgo: Int,
        @SerializedName("name")
        val name: String,
        @SerializedName("public_key")
        val publicKey: String,
        @SerializedName("sign_algo")
        val signAlgo: String,
        @SerializedName("weight")
        val weight: String,
    )
}

data class UpdateDeviceParams(
    @SerializedName("device_id")
    val deviceId: String
)

data class LocationInfo(
    @SerializedName("as")
    val asValue: String,

    @SerializedName("city")
    val city: String,

    @SerializedName("country")
    val country: String,

    @SerializedName("countryCode")
    val countryCode: String,

    @SerializedName("isp")
    val isp: String,

    @SerializedName("lat")
    val lat: Double,

    @SerializedName("lon")
    val lon: Double,

    @SerializedName("org")
    val org: String,

    @SerializedName("query")
    val query: String,

    @SerializedName("region")
    val region: String,

    @SerializedName("regionName")
    val regionName: String,

    @SerializedName("status")
    val status: String,

    @SerializedName("timezone")
    val timezone: String,

    @SerializedName("zip")
    val zip: String
)

data class DeviceInfoRequest(
    @SerializedName("city")
    val city: String,

    @SerializedName("country")
    val country: String,

    @SerializedName("countryCode")
    val countryCode: String,

    @SerializedName("device_id")
    val device_id: String,

    @SerializedName("ip")
    val ip: String,

    @SerializedName("isp")
    val isp: String,

    @SerializedName("lat")
    val lat: Double,

    @SerializedName("lon")
    val lon: Double,

    @SerializedName("name")
    val name: String,

    @SerializedName("org")
    val org: String,

    @SerializedName("regionName")
    val regionName: String,

    @SerializedName("type")
    val type: String,

    @SerializedName("user_agent")
    val user_agent: String,

    @SerializedName("zip")
    val zip: String
)
