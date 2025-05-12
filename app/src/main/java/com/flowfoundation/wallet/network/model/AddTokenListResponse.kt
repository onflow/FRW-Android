package com.flowfoundation.wallet.network.model

import android.os.Parcelable
import com.flowfoundation.wallet.manager.flowjvm.CadenceScript
import com.flowfoundation.wallet.utils.svgToPng
import com.flowfoundation.wallet.wallet.removeAddressPrefix
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

data class AddTokenListResponse(
    @SerializedName("tokens") val tokens: List<TokenInfo>
)

@Parcelize
data class TokenInfo(
    @SerializedName("chainId") val chainId: Int?,
    @SerializedName("address") val address: String,
    @SerializedName("contractName") val contractName: String?,
    @SerializedName("path") val path: TokenPath?,
    @SerializedName("symbol") val symbol: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("decimals") val decimals: Int,
    @SerializedName("logoURI") val logoURI: String?,
    @SerializedName("extensions") val extensions: TokenExtensions?,
    @SerializedName("isVerified") val isVerified: Boolean = false,
    @SerializedName("evmAddress") val evmAddress: String? = null,
): Parcelable {

    fun isSameCoin(contractId: String): Boolean {
        return this.contractId().equals(contractId, true)
    }

    fun contractId(): String {
        return "A.${address.removeAddressPrefix()}.${contractName.orEmpty()}"
    }

    fun tokenName(): String {
        return name.orEmpty()
    }

    fun tokenSymbol(): String {
        return symbol.orEmpty()
    }

    fun icon(): String {
        return if (logoURI?.endsWith(".svg") == true) {
            logoURI.svgToPng()
        } else {
            logoURI.orEmpty()
        }
    }
}

@Parcelize
data class TokenPath(
    @SerializedName("vault") val vault: String?,
    @SerializedName("receiver") val receiver: String?,
    @SerializedName("balance") val balance: String?
): Parcelable

@Parcelize
data class TokenExtensions(
    @SerializedName("website") val website: String?,
): Parcelable

fun TokenInfo.formatCadence(cadenceScript: CadenceScript): String {
    return cadenceScript.getScript().replace("<Token>", contractName.orEmpty())
        .replace("<TokenAddress>", address)
        .replace("<TokenReceiverPath>", path?.receiver.orEmpty())
        .replace("<TokenBalancePath>", path?.balance.orEmpty())
        .replace("<TokenStoragePath>", path?.vault.orEmpty())
}