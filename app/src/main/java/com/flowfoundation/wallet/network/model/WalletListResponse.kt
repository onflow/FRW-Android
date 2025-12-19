package com.flowfoundation.wallet.network.model

import android.os.Parcelable
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
    @SerializedName("id")
    val id: String,
    @SerializedName("username")
    val username: String,
    @SerializedName("wallets")
    val wallets: List<WalletData>?
)

@Serializable
data class WalletData(
    @SerializedName("blockchain")
    val blockchain: List<BlockchainData>?,
    @SerializedName("name")
    val name: String
)

@Serializable
@Parcelize
data class BlockchainData(
    @SerializedName("address")
    val address: String,
    @SerialName("chain_id")  // Needed: property name "chainId" differs from JSON key "chain_id"
    @SerializedName("chain_id")
    val chainId: String
) : Parcelable
