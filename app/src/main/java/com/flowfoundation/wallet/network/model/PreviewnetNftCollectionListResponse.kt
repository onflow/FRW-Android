package com.flowfoundation.wallet.network.model


import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.manager.config.NftCollection

data class PreviewnetNftCollectionListResponse(
    @SerializedName("tokens")
    val tokens: List<NftCollection>,
    @SerializedName("status")
    val status: Int?
)
