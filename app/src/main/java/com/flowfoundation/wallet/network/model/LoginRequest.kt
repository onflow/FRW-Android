package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName

/**
 * V3 Login request (deprecated - kept for backwards compatibility)
 * @deprecated Use LoginV4Request instead
 */
data class LoginRequest(
    @SerializedName("signature")
    val signature: String,

    @SerializedName("account_key")
    val accountKey: AccountKey,

    @SerializedName("device_info")
    val deviceInfo: DeviceInfoRequest?
)

/**
 * V4 Login request
 * Uses /v4/login endpoint with signature verification
 * Similar structure to RegisterRequest but without username
 */
data class LoginV4Request(
    @SerializedName("flow_account_info")
    val flowAccountInfo: FlowAccountInfo,

    @SerializedName("evm_account_info")
    val evmAccountInfo: EvmAccountInfo? = null,

    @SerializedName("device_info")
    val deviceInfo: DeviceInfoRequest? = null
)