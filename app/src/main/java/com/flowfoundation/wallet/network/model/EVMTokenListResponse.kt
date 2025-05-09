package com.flowfoundation.wallet.network.model

import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.flowfoundation.wallet.manager.token.model.FungibleTokenType
import com.google.gson.annotations.SerializedName

data class EVMTokenListResponse(
    @SerializedName("data")
    val data: List<EVMToken>?,
    @SerializedName("status")
    val status: Int?,
    @SerializedName("message")
    val message: String?
)

data class EVMToken(
    @SerializedName("chainId")
    val chainId: Int?,
    @SerializedName("address")
    val address: String?,
    @SerializedName("symbol")
    val symbol: String?,
    @SerializedName("name")
    val name: String?,
    @SerializedName("decimals")
    val decimals: Int?,
    @SerializedName("logoURI")
    val logoURI: String?,
    @SerializedName("flowIdentifier")
    val flowIdentifier: String?,
    @SerializedName("balance")
    val balance: String?,
    @SerializedName("priceInUSD")
    val priceInUSD: String?,
    @SerializedName("balanceInUSD")
    val balanceInUSD: String?,
    @SerializedName("priceInFLOW")
    val priceInFLOW: String?,
    @SerializedName("balanceInFLOW")
    val balanceInFLOW: String?,
    @SerializedName("currency")
    val currency: String?,
    @SerializedName("priceInCurrency")
    val priceInCurrency: String?,
    @SerializedName("balanceInCurrency")
    val balanceInCurrency: String?
)

fun EVMToken.toFungibleToken(): FungibleToken {
    return FungibleToken(
        name = this.name.orEmpty(),
        symbol = this.symbol.orEmpty(),
        logoURI = this.logoURI,
        decimals = this.decimals,
        balance = this.balance,
        currency = this.currency,
        priceInCurrency = this.priceInCurrency,
        balanceInCurrency = this.balanceInCurrency,
        balanceInUSD = this.balanceInUSD,
        isVerified = false,
        tokenType = FungibleTokenType.EVM,
        flowIdentifier = this.flowIdentifier,
        flowAddress = null,
        evmAddress = this.address,
        flowContractName = null,
        flowStoragePath = null,
        flowReceiverPath = null,
        flowBalancePath = null,
        flowSocialsWebsiteUrl = null,
        evmChainId = this.chainId
    )
}