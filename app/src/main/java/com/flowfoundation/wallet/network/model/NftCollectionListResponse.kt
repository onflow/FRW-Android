package com.flowfoundation.wallet.network.model


import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.manager.config.NftCollection

data class NftCollectionListResponse(
    @SerializedName("data")
    val data: List<NftCollection>,
    @SerializedName("status")
    val status: Int?
)
