package com.flowfoundation.wallet.manager.account.model

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

enum class ValidateTransactionResult {
    SUCCESS,
    FAILURE,
    BALANCE_INSUFFICIENT,
    STORAGE_INSUFFICIENT
}

data class AccountInfo(
    val address: String,
    val balance: BigDecimal,
    val availableBalance: BigDecimal,
    val storageUsed: Long,
    val storageCapacity: Long,
    val storageFlow: BigDecimal
)

data class AccountInfoInner(
    @SerializedName("type")
    val type: String?,
    @SerializedName("value")
    val value: Value?
) {
    data class Value(
        @SerializedName("fields")
        val fields: List<Field?>?,
        @SerializedName("id")
        val id: String?
    ) {
        data class Field(
            @SerializedName("name")
            val name: String?,
            @SerializedName("value")
            val value: Value2?
        ) {
            data class Value2(
                @SerializedName("type")
                val type: String?,
                @SerializedName("value")
                val value: String?
            )
        }
    }
}

fun AccountInfoInner.Value?.getByName(name: String): String? = this?.fields?.firstOrNull { it?.name == name }?.value?.value
