package com.flowfoundation.wallet.page.wallet.model

import com.google.gson.annotations.SerializedName


sealed class AvatarData {
  data class Icon(
    @SerializedName("url")
    val url: String
  ) : AvatarData()
  data class Emoji(
    @SerializedName("emojiId")
    val emojiId: Int
  ) : AvatarData()
}
