package com.flowfoundation.wallet.page.token.custom.model

import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.flowfoundation.wallet.manager.token.model.FungibleTokenType
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.utils.svgToPng
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable


@Serializable
data class CustomTokenItem(
    @SerializedName("contractAddress")
    val contractAddress: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("symbol")
    val symbol: String,
    @SerializedName("decimal")
    val decimal: Int,
    @SerializedName("icon")
    val icon: String,
    @SerializedName("contractName")
    val contractName: String?,
    @SerializedName("flowIdentifier")
    val flowIdentifier: String?,
    @SerializedName("evmAddress")
    val evmAddress: String?,
    @SerializedName("userId")
    val userId: String?,
    @SerializedName("userAddress")
    val userAddress: String?,
    @SerializedName("chainId")
    val chainId: Int?,
    @SerializedName("tokenType")
    val tokenType: TokenType
) {

    fun icon(): String {
        return if (icon.endsWith(".svg")) {
            icon.svgToPng()
        } else {
            icon
        }
    }

    fun isSameToken(chainId: Int? = 0, address: String): Boolean {
        return chainId == this.chainId && this.contractAddress.equals(address, true)
    }

    fun isEnable(): Boolean {
        return name.isNotEmpty() && symbol.isNotEmpty() && decimal > 0
    }

    fun isWalletTokenType(): Boolean {
        return tokenType == if (WalletManager.isEVMAccountSelected()) {
            TokenType.EVM
        } else {
            TokenType.FLOW
        }
    }

}

fun CustomTokenItem.toFungibleToken(): FungibleToken {
    return FungibleToken(
        name = this.name,
        symbol = this.symbol,
        logoURI = this.icon,
        decimals = this.decimal,
        balance = null,
        currency = null,
        priceInCurrency = null,
        balanceInCurrency = null,
        balanceInUSD = null,
        isVerified = false,
        tokenType = when (this.tokenType) {
            TokenType.EVM -> FungibleTokenType.EVM
            TokenType.FLOW -> FungibleTokenType.FLOW
        },
        flowIdentifier = this.flowIdentifier,
        flowAddress = if (this.tokenType == TokenType.FLOW) this.contractAddress else null,
        evmAddress = if (this.tokenType == TokenType.EVM) this.contractAddress else this.evmAddress,
        flowContractName = this.contractName,
        flowStoragePath = null,
        flowReceiverPath = null,
        flowBalancePath = null,
        flowSocialsWebsiteUrl = null,
        evmChainId = this.chainId
    )
}

@Serializable
enum class TokenType {
    @SerializedName("evm")
    EVM,

    @SerializedName("flow")
    FLOW
}