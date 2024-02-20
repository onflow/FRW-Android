package io.outblock.lilico.page.profile.subpage.wallet.device.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class DeviceModel(
    @SerializedName("city")
    val city: String,

    @SerializedName("continent")
    val continent: String,

    @SerializedName("continentCode")
    val continentCode: String,

    @SerializedName("country")
    val country: String,

    @SerializedName("countryCode")
    val countryCode: String,

    @SerializedName("created_at")
    val created_at: String,

    @SerializedName("currency")
    val currency: String,

    @SerializedName("device_name")
    val device_name: String,

    @SerializedName("device_type")
    val device_type: Int,

    @SerializedName("district")
    val district: String,

    @SerializedName("id")
    val id: String,

    @SerializedName("ip")
    val ip: String,

    @SerializedName("isp")
    val isp: String,

    @SerializedName("lat")
    val lat: Double,

    @SerializedName("lon")
    val lon: Double,

    @SerializedName("org")
    val org: String,

    @SerializedName("regionName")
    val regionName: String,

    @SerializedName("updated_at")
    val updated_at: String,

    @SerializedName("user_agent")
    val user_agent: String,

    @SerializedName("user_id")
    val user_id: String,

    @SerializedName("wallet_id")
    val wallet_id: Int,

    @SerializedName("walletsand_id")
    val walletsand_id: Int,

    @SerializedName("wallettest_id")
    val wallettest_id: Int,

    @SerializedName("zip")
    val zip: String
) : Parcelable

data class DeviceTitle(
    @SerializedName("text")
    val text: String
)

@Parcelize
data class DeviceKeyModel(
    @SerializedName("device_id")
    val deviceId: String,
    @SerializedName("key_id")
    val keyId: Int? = null,
    @SerializedName("device_model")
    val deviceModel: DeviceModel,
): Parcelable
