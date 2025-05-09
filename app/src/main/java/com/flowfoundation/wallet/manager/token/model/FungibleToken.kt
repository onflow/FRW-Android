package com.flowfoundation.wallet.manager.token.model

import com.flowfoundation.wallet.utils.svgToPng
import com.flowfoundation.wallet.wallet.removeAddressPrefix
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable
import java.math.BigDecimal


enum class FungibleTokenType {
    EVM,
    FLOW,
}

@Serializable
data class FungibleToken(
    @SerializedName("name")
    val name: String,
    @SerializedName("symbol")
    val symbol: String,
    @SerializedName("logoURI")
    val logoURI: String?,
    @SerializedName("decimals")
    val decimals: Int?,
    @SerializedName("balance")
    val balance: String?,
    @SerializedName("currency")
    val currency: String?,
    @SerializedName("priceInCurrency")
    val priceInCurrency: String?,
    @SerializedName("balanceInCurrency")
    val balanceInCurrency: String?,
    @SerializedName("balanceInUSD")
    val balanceInUSD: String?,
    @SerializedName("isVerified")
    val isVerified: Boolean,
    @SerializedName("tokenType")
    val tokenType: FungibleTokenType,
    @SerializedName("flowIdentifier")
    val flowIdentifier: String?,
    @SerializedName("flowAddress")
    val flowAddress: String?,
    @SerializedName("evmAddress")
    val evmAddress: String?,

    @SerializedName("flowContractName")
    val flowContractName: String?,
    @SerializedName("flowStoragePath")
    val flowStoragePath: String?,
    @SerializedName("flowReceiverPath")
    val flowReceiverPath: String?,
    @SerializedName("flowBalancePath")
    val flowBalancePath: String?,

    @SerializedName("flowSocialsWebsiteUrl")
    val flowSocialsWebsiteUrl: String?,

    @SerializedName("evmChainId")
    val evmChainId: Int?

) {

    fun contractId(): String {
        return "A.${tokenAddress().removeAddressPrefix()}.${tokenContractName()}"
    }

    fun tokenDecimal(): Int {
        return decimals ?: when (tokenType) {
            FungibleTokenType.EVM -> 18
            FungibleTokenType.FLOW -> 8
        }
    }

    fun tokenBalance(): BigDecimal {
        return if (balance.isNullOrBlank()) {
            BigDecimal.ZERO
        } else {
            BigDecimal(balance)
        }
    }

    fun tokenPrice(): BigDecimal {
        return if (priceInCurrency.isNullOrBlank()) {
            BigDecimal.ZERO
        } else {
            BigDecimal(priceInCurrency)
        }
    }

    fun tokenBalancePrice(): BigDecimal {
        return if (balanceInCurrency.isNullOrBlank()) {
            BigDecimal.ZERO
        } else {
            BigDecimal(balanceInCurrency)
        }
    }

    fun tokenBalanceInUSD(): BigDecimal {
        return if (balanceInUSD.isNullOrBlank()) {
            BigDecimal.ZERO
        } else {
            BigDecimal(balanceInUSD)
        }
    }

    fun tokenIcon(): String {
        if (logoURI.isNullOrEmpty()) {
            return "https://lilico.app/placeholder-2.0.png"
        }
        if (logoURI.endsWith(".svg")) {
            return logoURI.svgToPng()
        }
        return logoURI
    }

    fun tokenAddress(): String {
        return when (tokenType) {
            FungibleTokenType.EVM -> evmAddress ?: ""
            FungibleTokenType.FLOW -> flowAddress ?: ""
        }
    }

    fun tokenContractName(): String {
        return when (tokenType) {
            FungibleTokenType.EVM -> ""
            FungibleTokenType.FLOW -> flowContractName ?: ""
        }
    }

    fun tokenIdentifier(): String {
        return when (tokenType) {
            FungibleTokenType.EVM -> flowIdentifier
                ?: "A.${tokenAddress().removeAddressPrefix()}.${tokenContractName()}"

            FungibleTokenType.FLOW -> flowIdentifier
                ?: "A.${tokenAddress().removeAddressPrefix()}.${tokenContractName()}.Vault"
        }
    }

    fun isFlowToken(): Boolean {
        return symbol.lowercase() == SYMBOL_FLOW
    }

    fun tokenWebsite(): String {
        return flowSocialsWebsiteUrl ?: ""
    }

    fun canBridgeToEVM(): Boolean {
        return evmAddress.isNullOrBlank().not()
    }

    fun canBridgeToCadence(): Boolean {
        return flowIdentifier.isNullOrBlank().not()
    }

    fun isSameToken(contractId: String): Boolean {
        return contractId.equals(contractId(), true)
    }

    companion object {
        const val SYMBOL_FLOW = "flow"
        const val SYMBOL_USDC = "usdc"
    }
}