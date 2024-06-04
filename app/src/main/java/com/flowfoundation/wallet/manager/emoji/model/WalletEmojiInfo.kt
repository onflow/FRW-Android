package com.flowfoundation.wallet.manager.emoji.model

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class WalletEmojiInfo(
    @SerializedName("address")
    val address: String,
    @SerializedName("emojiId")
    val emojiId: Int,
    @SerializedName("emojiName")
    val emojiName: String
)
