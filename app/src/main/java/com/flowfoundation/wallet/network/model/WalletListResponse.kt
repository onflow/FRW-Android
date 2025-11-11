package com.flowfoundation.wallet.network.model

import android.os.Parcelable
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.wallet.toAddress
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

class WalletListResponse(
    @SerializedName("data")
    val data: WalletListData?,

    @SerializedName("message")
    val message: String,

    @SerializedName("status")
    val status: Int,
)

@Serializable
data class WalletListData(
    @SerialName("id")
    @SerializedName("id")
    val id: String,
    @SerialName("username")
    @SerializedName("username")
    val username: String,
    @SerialName("wallets")
    @SerializedName("wallets")
    val wallets: List<WalletData>?
) {
    fun wallet(): WalletData? {
        return wallets?.firstOrNull { it.network() == chainNetWorkString() }
    }

    fun walletAddress(): String? = wallet()?.address()?.toAddress()

    fun chainNetworkWallet(chainNetWork: String?): WalletData? {
        return wallets?.firstOrNull { it.network() == chainNetWork }
    }
}

@Serializable
data class WalletData(
    @SerialName("blockchain")
    @SerializedName("blockchain")
    val blockchain: List<BlockchainData>?,
    @SerialName("name")
    @SerializedName("name")
    val name: String
) {
    fun address() = blockchain?.firstOrNull()?.address?.toAddress()

    fun network() = blockchain?.firstOrNull()?.chainId
}

@Serializable
@Parcelize
data class BlockchainData(
    @SerialName("address")
    @SerializedName("address")
    val address: String,
    @SerialName("chain_id")
    @SerializedName("chain_id")
    val chainId: String
) : Parcelable
