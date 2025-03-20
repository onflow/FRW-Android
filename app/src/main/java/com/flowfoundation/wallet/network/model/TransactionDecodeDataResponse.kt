package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName


data class TransactionDecodeDataResponse(
    val abi: List<Any> = emptyList(),
    val name: String = "",
    @SerializedName("is_verified")
    val isVerified: Boolean = false,
    val decodedData: DecodedData? = null,
    val status: Int = 0
)

data class DecodedData(
    val allPossibilities: List<Possibility> = emptyList(),
    val name: String? = null,
    val params: List<ParamData>? = null
) {
    fun getFunctionName(): String? {
        return when {
            name != null -> name
            allPossibilities.isNotEmpty() -> allPossibilities.first().function
            else -> null
        }
    }

    fun getParams(): Any? {
        return when {
            params != null -> params
            allPossibilities.isNotEmpty() -> allPossibilities.first().params
            else -> null
        }
    }

    fun isFunctionFormat(): Boolean {
        return name != null && params != null
    }
}

@Suppress("UNCHECKED_CAST")
data class ParamData(
    val name: String = "",
    val value: Any? = null,
    val type: String = ""
) {

    fun getStringValue(): String? {
        return value as? String
    }

    fun getStringListValue(): List<String>? {
        return value as? List<String>
    }
}

@Suppress("UNCHECKED_CAST")
data class Possibility(
    val function: String = "",
    val params: Any? = null
) {

    fun getStringParams(): List<String>? {
        return params as? List<String>
    }

    fun getMapParams(): Map<String, String>? {
        return params as? Map<String, String>
    }

    fun isStringListParams(): Boolean = params is List<*>
    fun isMapParams(): Boolean = params is Map<*, *>
}
