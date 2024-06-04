package com.flowfoundation.wallet.manager.account.model

import com.google.gson.annotations.SerializedName


data class EVMTokenBalanceResponse (
    @SerializedName("data")
    val data: List<EVMTokenBalance>?,
    @SerializedName("status")
    val status: Int?
)

data class EVMTokenBalance(
    @SerializedName("chainId")
    val chainId: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("address")
    val address: String,
    @SerializedName("decimals")
    val decimal: Int,
    @SerializedName("logoURI")
    val icon: String,
    @SerializedName("symbol")
    val symbol: String,
    @SerializedName("flowIdentifier")
    val flowIdentifier: String?,
    @SerializedName("balance")
    val balance: String
)