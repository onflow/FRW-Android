package com.flowfoundation.wallet.page.token.custom.model

import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.FlowCoinType
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

    fun toFlowCoin(coinType: FlowCoinType? = FlowCoinType.EVM): FlowCoin {
        return FlowCoin(
            chainId = chainId,
            name = name,
            address = contractAddress,
            contractName = contractName,
            storagePath = null,
            decimal = decimal,
            icon = icon,
            symbol = symbol,
            extensions = null,
            flowIdentifier = flowIdentifier,
            evmAddress = evmAddress,
            type = coinType
        )
    }
}

@Serializable
enum class TokenType {
    @SerializedName("evm")
    EVM,

    @SerializedName("flow")
    FLOW
}