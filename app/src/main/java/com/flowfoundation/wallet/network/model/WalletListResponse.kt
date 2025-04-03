package com.flowfoundation.wallet.network.model

import android.os.Parcelable
import com.flowfoundation.wallet.manager.app.NETWORK_NAME_MAINNET
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.wallet.toAddress
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

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
    @SerializedName("id")
    val id: String,
    @SerializedName("username")
    val username: String,
    @SerializedName("wallets")
    val wallets: List<WalletData>?
) {
    fun wallet(): WalletData? {
        return wallets?.firstOrNull { it.network() == chainNetWorkString() }
    }

    fun walletAddress(): String? = wallet()?.address()?.toAddress()

    fun mainnetWallet(): WalletData? {
        return wallets?.firstOrNull { it.network() == NETWORK_NAME_MAINNET }
    }

    fun chainNetworkWallet(chainNetWork: String?): WalletData? {
        return wallets?.firstOrNull { it.network() == chainNetWork }
    }
}

@Serializable
data class WalletData(
    @SerializedName("blockchain")
    val blockchain: List<BlockchainData>?,
    @SerializedName("name")
    val name: String
) {
    fun address() = blockchain?.firstOrNull()?.address?.toAddress()

    fun network() = blockchain?.firstOrNull()?.chainId
}

@Serializable
@Parcelize
data class BlockchainData(
    @SerializedName("address")
    val address: String,
    @SerializedName("chain_id")
    val chainId: String
) : Parcelable
