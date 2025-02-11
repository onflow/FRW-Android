package com.flowfoundation.wallet.manager.account.model

import kotlinx.serialization.Serializable

enum class ValidateTransactionResult {
    SUCCESS,
    FAILURE,
    BALANCE_INSUFFICIENT,
    STORAGE_INSUFFICIENT,
    STORAGE_INSUFFICIENT_AFTER_ACTION
}

enum class StorageLimitDialogType {
    LIMIT_REACHED_WARNING,
    LIMIT_AFTER_ACTION_WARNING,
    LIMIT_REACHED_ERROR
}

@Serializable
data class AccountInfo(
    val address: String,
    val balance: Double,
    val availableBalance: Double,
    val storageUsed: Long,
    val storageCapacity: Long,
    val storageFlow: Double
)
