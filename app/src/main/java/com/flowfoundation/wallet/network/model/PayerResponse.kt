package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.manager.config.AppConfig

/**
 * Data class representing the surge info from the payer status API
 */
data class SurgeInfo(
    @SerializedName("active")
    val active: Boolean = false,

    @SerializedName("multiplier")
    val multiplier: String? = null,  // Changed from Double to String as API returns string

    @SerializedName("expiresAt")
    val expiresAt: Long? = null,

    @SerializedName("ttlSeconds")
    val ttlSeconds: Long? = null,

    @SerializedName("sampledAt")
    val sampledAt: Long? = null,

    @SerializedName("maxFee")
    val maxFee: Double? = null
) {
    // Helper method to get multiplier as Double
    fun getMultiplierAsDouble(): Double {
        return multiplier?.toDoubleOrNull() ?: 1.0
    }
}

/**
 * Data class representing the payer info
 */
data class PayerInfo(
    @SerializedName("available")
    val available: Boolean = false,

    @SerializedName("address")
    val address: String? = null,

    @SerializedName("keyIndex")
    val keyIndex: Int? = null
) {
    /**
     * Get payer address, fallback to AppConfig payer address if not available
     */
    fun address(): String {
        return address ?: AppConfig.payer().address
    }

    /**
     * Get payer key ID, fallback to AppConfig payer key ID if not available
     */
    fun keyId(): Int {
        return keyIndex ?: AppConfig.payer().keyId
    }
}

/**
 * Data class representing the bridge payer info
 */
data class BridgePayerInfo(
    @SerializedName("available")
    val available: Boolean = false,

    @SerializedName("address")
    val address: String? = null,

    @SerializedName("keyIndex")
    val keyIndex: Int? = null
) {
    fun address(): String {
        return address ?: AppConfig.bridgeFeePayer().address
    }
}

/**
 * Data class representing the payload from payer/status endpoint
 */
data class PayerStatusPayload(
    @SerializedName("statusVersion")
    val statusVersion: Int = 1,

    @SerializedName("surge")
    val surge: SurgeInfo? = null,

    @SerializedName("feePayer")
    val feePayer: PayerInfo? = null,

    @SerializedName("bridgePayer")
    val bridgePayer: BridgePayerInfo? = null,

    @SerializedName("updatedAt")
    val updatedAt: Long? = null
)

/**
 * Data class representing the API response envelope
 */
data class PayerStatusResponse(
    @SerializedName("status")
    val status: Int,

    @SerializedName("data")
    val data: PayerStatusPayload? = null,

    @SerializedName("message")
    val message: String? = null
)

/**
 * Data class representing the error response for surge pricing (429 responses)
 */
data class PayerErrorResponse(
    // The HTTP status code (not from JSON, set manually)
    val status: Int
) {
    companion object {
        private const val SURGE_PRICING_CODE = 429
    }

    fun isSurgePricing(): Boolean = status == SURGE_PRICING_CODE

    fun isServerError(): Boolean = status in 500..599
}
