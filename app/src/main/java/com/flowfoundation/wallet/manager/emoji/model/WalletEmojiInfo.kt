package com.flowfoundation.wallet.manager.emoji.model

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class WalletEmojiInfo(
    @SerialName("address")
    @SerializedName("address")
    val address: String,
    @SerialName("emojiId")
    @SerializedName("emojiId")
    val emojiId: Int,
    @SerialName("emojiName")
    @SerializedName("emojiName")
    val emojiName: String
)
