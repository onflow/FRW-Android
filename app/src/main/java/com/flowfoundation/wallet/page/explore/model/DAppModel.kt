package com.flowfoundation.wallet.page.explore.model

import com.google.gson.annotations.SerializedName

data class DAppModel(
    @SerializedName("category")
    val category: String,
    @SerializedName("description")
    val description: String,
    @SerializedName("logo")
    val logo: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("testnet_url")
    val testnetUrl: String?,
    @SerializedName("url")
    val url: String?,
)

data class DAppTagModel(
    @SerializedName("category")
    val category: String,
    @SerializedName("category")
    val isSelected: Boolean = false,
)