package com.flowfoundation.wallet.manager.emoji.model

import kotlinx.serialization.Serializable

@Serializable
data class WalletEmojiInfo(
    val address: String,
    val emojiId: Int,
    val emojiName: String
)
