package com.flowfoundation.wallet.manager.evm

import com.google.gson.annotations.SerializedName


data class EVMAccount(
    @SerializedName("address")
    val address: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("icon")
    val icon: String
)