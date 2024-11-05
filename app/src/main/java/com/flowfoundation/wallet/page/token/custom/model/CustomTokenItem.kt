package com.flowfoundation.wallet.page.token.custom.model

import com.flowfoundation.wallet.manager.coin.FlowCoin
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
    val icon: String?,
    @SerializedName("contractName")
    val contractName: String?,
    @SerializedName("flowIdentifier")
    val flowIdentifier: String?,
    @SerializedName("evmAddress")
    val evmAddress: String?
) {
    fun isEnable(): Boolean {
        return name.isNotEmpty() && symbol.isNotEmpty() && decimal > 0
    }

    fun toFlowCoin(): FlowCoin {
        return FlowCoin(
            chainId = null,
            name = name,
            address = contractAddress,
            contractName = contractName,
            storagePath = null,
            decimal = decimal,
            icon = icon ?: "https://lilico.app/placeholder-2.0.png",
            symbol = symbol,
            extensions = null,
            flowIdentifier = flowIdentifier,
            evmAddress = evmAddress
        )
    }
}
