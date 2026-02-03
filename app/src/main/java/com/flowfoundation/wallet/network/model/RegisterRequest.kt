package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName

/**
 * Flow account information for v4 registration
 * Contains account key and signature (signature of Firebase JWT)
 */
data class FlowAccountInfo(
    @SerializedName("account_key")
    val accountKey: AccountKey,

    @SerializedName("signature")
    val signature: String
)

/**
 * EVM account information for v4 registration (optional)
 * Contains EOA address and signature
 */
data class EvmAccountInfo(
    @SerializedName("eoa_address")
    val eoaAddress: String,

    @SerializedName("signature")
    val signature: String
)

/**
 * V4 Register request
 * Uses /v4/register endpoint with signature verification
 */
data class RegisterRequest(
    @SerializedName("flow_account_info")
    val flowAccountInfo: FlowAccountInfo,

    @SerializedName("evm_account_info")
    val evmAccountInfo: EvmAccountInfo? = null,

    @SerializedName("username")
    val username: String,

    @SerializedName("device_info")
    val deviceInfo: DeviceInfoRequest? = null
)