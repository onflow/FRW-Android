package io.outblock.lilico.manager.walletconnect.model

import com.google.gson.annotations.SerializedName
import com.nftco.flow.sdk.HashAlgorithm
import com.nftco.flow.sdk.SignatureAlgorithm
import io.outblock.lilico.network.model.AccountKey
import io.outblock.lilico.network.model.DeviceInfoRequest

data class WCWalletResponse(
    @SerializedName("method")
    val method: String? = "",
    @SerializedName("status")
    val status: String? = "",
    @SerializedName("message")
    val message: String? = "",
    @SerializedName("data")
    val data: WCWalletInfo? = null
)

data class WCWalletInfo(
    @SerializedName("userAvatar")
    val userAvatar: String,
    @SerializedName("userName")
    val userName: String,
    @SerializedName("walletAddress")
    val walletAddress: String
)

data class WCAccountRequest(
    @SerializedName("method")
    val method: String? = "",
    @SerializedName("status")
    val status: String? = "",
    @SerializedName("message")
    val message: String? = "",
    @SerializedName("data")
    val data: WCAccountInfo? = null
)

data class WCAccountInfo(
    @SerializedName("accountKey")
    val accountKey: WCAccountKey,

    @SerializedName("deviceInfo")
    val deviceInfo: WCDeviceInfo?
)

data class WCAccountKey(
    @SerializedName("hashAlgo")
    val hashAlgo: Int = HashAlgorithm.SHA2_256.index,

    @SerializedName("signAlgo")
    val signAlgo: Int = SignatureAlgorithm.ECDSA_P256.index,

    @SerializedName("weight")
    val weight: Int = 1000,

    @SerializedName("publicKey")
    val publicKey: String,
) {
    fun getApiAccountKey(): AccountKey {
        return AccountKey(
            hashAlgo, signAlgo, weight, publicKey
        )
    }
}

data class WCDeviceInfo(
    @SerializedName("city")
    val city: String,

    @SerializedName("country")
    val country: String,

    @SerializedName("countryCode")
    val countryCode: String,

    @SerializedName("deviceId")
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

    @SerializedName("userAgent")
    val user_agent: String,

    @SerializedName("zip")
    val zip: String
) {
    fun getApiDeviceInfo(): DeviceInfoRequest {
        return DeviceInfoRequest(city, country, countryCode, device_id, ip, isp, lat, lon, name, org, regionName, type, user_agent, zip)
    }
}

fun walletConnectWalletInfoResponse(
    userId: String,
    userAvatar: String,
    userName: String,
    walletAddress: String
): String {
    return """
        {
            "method": "${WalletConnectMethod.ACCOUNT_INFO.value}",
            "status": "200",
            "message": "success",
            "data": ${walletInfo(userId, userAvatar, userName, walletAddress)}
        }
    """.trimIndent()
}

private fun walletInfo(
    userId: String,
    userAvatar: String,
    userName: String,
    walletAddress: String
): String {
    return """
        {
            "userId": "$userId",
            "userAvatar": "$userAvatar",
            "userName": "$userName",
            "walletAddress": "$walletAddress"
        }
    """.trimIndent()
}

fun walletConnectDeviceKeyRequest(
    accountInfoJson: String
): String {
    return """
        {
            "method": "${WalletConnectMethod.ADD_DEVICE_KEY.value}",
            "status": "200",
            "message": "success",
            "data": "$accountInfoJson"
        }
    """.trimIndent()
}

private fun accountInfo(
    accountKey: AccountKey,
    deviceInfo: DeviceInfoRequest?
): String {
    return """
        {
            "account_key": {
                "hash_algo": ${accountKey.hashAlgo},
                "sign_algo": ${accountKey.signAlgo},
                "weight": ${accountKey.weight},
                "public_key": "${accountKey.publicKey}"
            },
            "device_info": ${if (deviceInfo == null) null else deviceInfo(deviceInfo)}
        }
    """.trimIndent()
}

private fun deviceInfo(deviceInfo: DeviceInfoRequest): String {
    return """
        {
            "city": "${deviceInfo.city}",
            "country": "${deviceInfo.country}",
            "countryCode": "${deviceInfo.countryCode}",
            "device_id": "${deviceInfo.device_id}",
            "ip": "${deviceInfo.ip}",
            "isp": "${deviceInfo.isp}",
            "lat": ${deviceInfo.lat},
            "lon": ${deviceInfo.lon},
            "name": "${deviceInfo.name}",
            "org": "${deviceInfo.org}",
            "regionName": "${deviceInfo.regionName}",
            "type": "${deviceInfo.type}",
            "user_agent": "${deviceInfo.user_agent}",
            "zip": "${deviceInfo.zip}"
        }
    """.trimIndent()
}

private fun accountKey(accountKey: AccountKey): String {
    return """
        
    """.trimIndent()
}
