package com.flowfoundation.wallet.manager.token

import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class DisplayTokenListCache(
    @SerializedName("hideDustTokens")
    val hideDustTokens: Boolean = false,
    @SerializedName("onlyShowVerifiedTokens")
    val onlyShowVerifiedTokens: Boolean = false,
    @SerializedName("displayTokenList")
    val displayTokenList: List<FungibleToken> = emptyList()
) 