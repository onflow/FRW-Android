package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName

data class ImportRequest(
    @SerializedName("address")
    val address: String,

    @SerializedName("username")
    val username: String,

    @SerializedName("account_key")
    val accountKey: AccountKey,

    @SerializedName("device_info")
    val deviceInfo: DeviceInfoRequest?
)