package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName

data class OutblockUser(
    @SerializedName("created_at")
    val createdAt: String?,

    @SerializedName("id")
    val id: String?,

    @SerializedName("password")
    val password: String?,

    @SerializedName("primary_wallet")
    val primaryWallet: String?,

    @SerializedName("updated_at")
    val updatedAt: String?,

    @SerializedName("username")
    val username: String?,
)