package com.flowfoundation.wallet.network.model

import com.flowfoundation.wallet.utils.svgToPng
import com.google.gson.annotations.SerializedName


data class EVMNFTCollectionsResponse(
    @SerializedName("data")
    val data: List<EVMNFTCollection>?,
    @SerializedName("status")
    val status: Int?
)

data class EVMNFTCollection(
    @SerializedName("chainId")
    val chainId: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("address")
    val address: String,
    @SerializedName("logoURI")
    val logo: String,
    @SerializedName("symbol")
    val symbol: String,
    @SerializedName("tokenURI")
    val tokenUri: String,
    @SerializedName("flowIdentifier")
    val flowIdentifier: String?,
    @SerializedName("balance")
    val balance: String,
    @SerializedName("nfts")
    val nftList: List<EVMNFTInfo>,
    @SerializedName("nftIds")
    val nftIds: List<String>
) {

    fun logo(): String {
        return if (logo.endsWith(".svg")) {
            logo.svgToPng()
        } else {
            logo
        }
    }

    fun getId(): String {
        val identifier = flowIdentifier?.split(".") ?: emptyList()
        return if (identifier.size > 2) {
            identifier[2]
        } else name
    }

    fun getContractName(): String {
        val identifier = flowIdentifier?.split(".") ?: emptyList()
        return if (identifier.size > 2) {
            identifier[2]
        } else name
    }

    fun getContractAddress(): String {
        val identifier = flowIdentifier?.split(".") ?: emptyList()
        return if (identifier.size > 1) {
            identifier[1]
        } else address
    }
}

data class EVMNFTInfo(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("thumbnail")
    val thumb: String
)
