package com.flowfoundation.wallet.manager.walletdata

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable
import java.io.Serializable as JavaSerializable

// Top Level Wallets
@Serializable
sealed class MainWallet : JavaSerializable {
    abstract val address: String
    abstract val name: String
    abstract val emojiId: Int
}

@Serializable
data class FlowWallet(
    @SerializedName("address")
    override val address: String,
    @SerializedName("name")
    override val name: String,
    @SerializedName("emojiId")
    override val emojiId: Int,
    @SerializedName("chainIdString")
    val chainIdString: String,
    @SerializedName("linkedWallets")
    val linkedWallets: List<LinkedWallet> = emptyList()
) : MainWallet()

@Serializable
data class EOAWallet(
    @SerializedName("address")
    override val address: String,
    @SerializedName("name")
    override val name: String,
    @SerializedName("emojiId")
    override val emojiId: Int,
    @SerializedName("index")
    val index: Int = 0
) : MainWallet()

// Linked Wallets (Children / COA)
@Serializable
sealed class LinkedWallet : JavaSerializable {
    abstract val address: String
    abstract val name: String
    abstract val emojiId: Int
}

@Serializable
data class ChildWallet(
    @SerializedName("address")
    override val address: String,
    @SerializedName("name")
    override val name: String,
    @SerializedName("emojiId")
    override val emojiId: Int,
    @SerializedName("icon")
    val icon: String,
    @SerializedName("pinTime")
    var pinTime: Long = 0,
) : LinkedWallet()

@Serializable
data class COAWallet(
    @SerializedName("address")
    override val address: String,
    @SerializedName("name")
    override val name: String,
    @SerializedName("emojiId")
    override val emojiId: Int
) : LinkedWallet()
