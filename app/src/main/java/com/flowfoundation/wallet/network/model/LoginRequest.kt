package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("signature")
    val signature: String,

    @SerializedName("account_key")
    val accountKey: AccountKey,

    @SerializedName("device_info")
    val deviceInfo: DeviceInfoRequest?
)