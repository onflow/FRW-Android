package com.flowfoundation.wallet.page.nft.move.model

import com.google.gson.annotations.SerializedName

data class CollectionInfo (
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("logo")
    val logo: String
)

data class CollectionDetailInfo(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("logo")
    val logo: String,
    @SerializedName("contractName")
    val contractName: String,
    @SerializedName("contractAddress")
    val contractAddress: String,
    @SerializedName("count")
    val count: Int,
    @SerializedName("isFlowCollection")
    val isFlowCollection: Boolean,
    @SerializedName("identifier")
    val identifier: String,
    @SerializedName("nftIdentifier")
    val nftIdentifier: String
)