package com.flowfoundation.wallet.manager.walletconnect.model

import com.flowfoundation.wallet.firebase.auth.firebaseUid
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.network.model.AccountKey
import com.flowfoundation.wallet.network.model.DeviceInfoRequest
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm

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

data class WCProxyAccountRequest(
    @SerializedName("method")
    val method: String? = "",
    @SerializedName("status")
    val status: String? = "",
    @SerializedName("message")
    val message: String? = "",
    @SerializedName("data")
    val data: WCProxyAccountInfo? = null
)

data class WCProxyAccountInfo(
    @SerializedName("deviceInfo")
    val deviceInfo: WCDeviceInfo?,
    @SerializedName("jwt")
    val jwt: String?,
)

data class WCAccountInfo(
    @SerializedName("accountKey")
    val accountKey: WCAccountKey,

    @SerializedName("deviceInfo")
    val deviceInfo: WCDeviceInfo?
)

data class WCAccountKey(
    @SerializedName("hashAlgo")
    val hashAlgo: Int = HashingAlgorithm.SHA2_256.cadenceIndex,

    @SerializedName("signAlgo")
    val signAlgo: Int = SigningAlgorithm.ECDSA_P256.cadenceIndex,

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
    val city: String?,

    @SerializedName("country")
    val country: String?,

    @SerializedName("countryCode")
    val countryCode: String?,

    @SerializedName("deviceId")
    val device_id: String,

    @SerializedName("ip")
    val ip: String?,

    @SerializedName("isp")
    val isp: String?,

    @SerializedName("lat")
    val lat: Double?,

    @SerializedName("lon")
    val lon: Double?,

    @SerializedName("name")
    val name: String,

    @SerializedName("org")
    val org: String?,

    @SerializedName("regionName")
    val regionName: String?,

    @SerializedName("type")
    val type: String,

    @SerializedName("userAgent")
    val user_agent: String,

    @SerializedName("zip")
    val zip: String?
) {
    fun getApiDeviceInfo(): DeviceInfoRequest {
        return DeviceInfoRequest(device_id, name, type, user_agent)
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

fun walletConnectProxyAccountResponse(
    signature: String,
    publicKey: String,
    hashAlgo: Int,
    signAlgo: Int,
    weight: Int
): String {
    return """
        {
            "method": "${WalletConnectMethod.PROXY_ACCOUNT.value}",
            "status": "200",
            "message": "success",
            "data": ${proxyAccountInfo(signature, publicKey, hashAlgo, signAlgo, weight)}
        }
    """.trimIndent()
}

private fun proxyAccountInfo(
    signature: String,
    publicKey: String,
    hashAlgo: Int,
    signAlgo: Int,
    weight: Int
): String {
    return """
        {
            "signature": "$signature",
            "userId": "${firebaseUid().orEmpty()}",
            "publicKey": "$publicKey",
            "hashAlgo": "$hashAlgo",
            "signAlgo": "$signAlgo",
            "weight": "$weight",
            "walletAddress": "${WalletManager.selectedWalletAddress()}"
        }
    """.trimIndent()
}
