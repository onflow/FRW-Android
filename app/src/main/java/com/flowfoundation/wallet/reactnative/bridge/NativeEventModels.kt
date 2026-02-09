//
//  NativeEventModels.kt
//  
//  Auto-generated from TypeScript bridge types
//  Do not edit manually
//

package com.flowfoundation.wallet.reactnative.bridge

import com.google.gson.annotations.SerializedName

class RNNativeEvent {
    data class KeyRotationCheckParams(
        @SerializedName("address")
        val address: String
    )

    data class KeyRotationCheckResult(
        @SerializedName("address")
        val address: String,
        @SerializedName("isBlocto")
        val isBlocto: Boolean
    )

    data class NativeRequestPayload(
        @SerializedName("requestId")
        val requestId: String,
        @SerializedName("eventName")
        val eventName: NativeEventName,
        @SerializedName("paramsJson")
        val paramsJson: String
    )

    data class NativeResponsePayload(
        @SerializedName("requestId")
        val requestId: String,
        @SerializedName("eventName")
        val eventName: NativeEventName,
        @SerializedName("resultJson")
        val resultJson: String?,
        @SerializedName("error")
        val error: String?
    )

    enum class NativeEventName {
        @SerializedName("keyRotationCheck") KEYROTATIONCHECK
    }

}
