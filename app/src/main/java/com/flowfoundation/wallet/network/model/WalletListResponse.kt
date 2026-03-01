package com.flowfoundation.wallet.network.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.ExperimentalSerializationApi

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
@OptIn(ExperimentalSerializationApi::class)
data class BlockchainData(
    @SerializedName("address")
    val address: String,
    @SerialName("chain_id")
    @SerializedName("chain_id")
    @JsonNames("chainId")
    val chainId: String = ""
) : Parcelable
