package com.flowfoundation.wallet.page.main.model

import com.google.gson.annotations.SerializedName


data class WalletAccountData(
    @SerializedName("address")
    val address: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("emojiId")
    val emojiId: Int,
    @SerializedName("isSelected")
    val isSelected: Boolean,
    @SerializedName("linkedAccounts")
    val linkedAccounts: List<LinkedAccountData> = emptyList(),
    @SerializedName("isEOAAccount")
    val isEOAAccount: Boolean = false
)

data class LinkedAccountData(
    @SerializedName("address")
    val address: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("icon")
    val icon: String? = null,
    @SerializedName("emojiId")
    val emojiId: Int,
    @SerializedName("isSelected")
    val isSelected: Boolean,
    @SerializedName("isCOAAccount")
    val isCOAAccount: Boolean
)

data class WalletItemData(
    @SerializedName("address")
    val address: String,
    @SerializedName("emojiId")
    val emojiId: Int,
    @SerializedName("emojiName")
    val emojiName: String,
    @SerializedName("isSelected")
    val isSelected: Boolean = false,
    @SerializedName("isEOAAccount")
    val isEOAAccount: Boolean = false,
    @SerializedName("isHidden")
    val isHidden: Boolean = false
)
