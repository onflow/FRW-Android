package com.flowfoundation.wallet.network.model

import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.flowfoundation.wallet.manager.token.model.FungibleTokenType
import com.google.gson.annotations.SerializedName

data class FlowTokenListResponse(
    @SerializedName("data")
    val data: FlowTokenData?,
    @SerializedName("status")
    val status: Int?
)

data class FlowTokenData(
    @SerializedName("result")
    val result: List<FlowToken>?,
    @SerializedName("storage")
    val storage: StorageInfo?,
)

data class FlowToken(
    @SerializedName("name")
    val name: String?,
    @SerializedName("symbol")
    val symbol: String?,
    @SerializedName("description")
    val description: String?,
    @SerializedName("logos")
    val logos: Logos?,
    @SerializedName("socials")
    val socials: Socials?,
    @SerializedName("displayBalance")
    val displayBalance: String?,
    @SerializedName("contractAddress")
    val contractAddress: String?,
    @SerializedName("contractName")
    val contractName: String?,
    @SerializedName("storagePath")
    val storagePath: Path?,
    @SerializedName("receiverPath")
    val receiverPath: Path?,
    @SerializedName("balancePath")
    val balancePath: Path?,
    @SerializedName("identifier")
    val identifier: String?,
    @SerializedName("logoURI")
    val logoURI: String?,
    @SerializedName("isVerified")
    val isVerified: Boolean?,
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
    val balanceInCurrency: String?,
    @SerializedName("evmAddress")
    val evmAddress: String?,
    @SerializedName("balance")
    val balance: String?
)

data class Logos(
    @SerializedName("file")
    val file: FileLogo?
)

data class FileLogo(
    @SerializedName("url")
    val url: String?
)

data class Socials(
    @SerializedName("website")
    val website: SocialLink?,
    @SerializedName("twitter")
    val twitter: SocialLink?
)

data class SocialLink(
    @SerializedName("url")
    val url: String?
)

data class Path(
    @SerializedName("domain")
    val domain: String?,
    @SerializedName("identifier")
    val identifier: String?
) {
    fun getPath(): String {
        if (domain.isNullOrBlank()) {
            return identifier.orEmpty()
        }
        return "/${domain}/${identifier.orEmpty()}"
    }
}

data class StorageInfo(
    @SerializedName("storageUsedInMB")
    val storageUsedInMB: String?,
    @SerializedName("storageAvailableInMB")
    val storageAvailableInMB: String?,
    @SerializedName("storageCapacityInMB")
    val storageCapacityInMB: String?,
    @SerializedName("lockedFLOWforStorage")
    val lockedFLOWforStorage: String?,
    @SerializedName("availableBalanceToUse")
    val availableBalanceToUse: String?
)

fun FlowToken.toFungibleToken(): FungibleToken {
    return FungibleToken(
        name = this.name.orEmpty(),
        symbol = this.symbol.orEmpty(),
        logoURI = this.logoURI ?: this.logos?.file?.url,
        decimals = null,
        balance = this.balance,
        currency = this.currency,
        priceInCurrency = this.priceInCurrency,
        balanceInCurrency = this.balanceInCurrency,
        balanceInUSD = this.balanceInUSD,
        isVerified = this.isVerified ?: false,
        tokenType = FungibleTokenType.FLOW,
        flowIdentifier = this.identifier,
        flowAddress = this.contractAddress,
        evmAddress = this.evmAddress,
        flowContractName = this.contractName,
        flowStoragePath = this.storagePath?.getPath(),
        flowReceiverPath = this.receiverPath?.getPath(),
        flowBalancePath = this.balancePath?.getPath(),
        flowSocialsWebsiteUrl = this.socials?.website?.url,
        evmChainId = null
    )
}
