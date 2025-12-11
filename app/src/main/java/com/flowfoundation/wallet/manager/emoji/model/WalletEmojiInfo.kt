package com.flowfoundation.wallet.manager.emoji.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WalletEmojiInfo(
    @SerialName("address")
    val address: String,
    @SerialName("emojiId")
    val emojiId: Int,
    @SerialName("emojiName")
    val emojiName: String
)
