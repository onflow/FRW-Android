package com.flowfoundation.wallet.network.model

import android.os.Parcelable
import com.flowfoundation.wallet.manager.config.NftCollectionConfig
import com.flowfoundation.wallet.wallet.removeAddressPrefix
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

data class NFTListResponse(
    @SerializedName("data")
    val data: NFTListData? = null,

    @SerializedName("message")
    val message: String,

    @SerializedName("status")
    val status: Int,
)

data class NFTListData(
    @SerializedName("nftCount")
    val nftCount: Int,
    @SerializedName("nfts")
    var nfts: List<Nft>?,
    @SerializedName("offset")
    var offset: String?
)

@Parcelize
data class Nft(
    @SerializedName("contract")
    val contract: NFTContract?,
    @SerializedName("description")
    val description: String?,
    @SerializedName("id")
    val id: String,
    @SerializedName("media")
    val media: List<NFTMedia>?,
    @SerializedName("metadata")
    val metadata: NFTMetadata?,
    @SerializedName("title")
    val title: String?,
    @SerializedName("postMedia")
    val postMedia: PostMedia,

    @SerializedName("collectionName")
    val collectionName: String,
    @SerializedName("collectionContractName")
    val collectionContractName: String?,
    @SerializedName("contractAddress")
    val collectionAddress: String,
    @SerializedName("collectionDescription")
    val collectionDescription: String,
    @SerializedName("collectionSquareImage")
    val collectionSquareImage: String,
    @SerializedName("collectionBannerImage")
    val collectionBannerImage: String,
    @SerializedName("collectionExternalURL")
    val collectionExternalURL: String,
    @SerializedName("traits")
    val traits: List<NftTraits>?,
    @SerializedName("flowIdentifier")
    val flowIdentifier: String?
) : Parcelable {
    fun uniqueId() = "${collectionName}.${contractName()}-${id}"

    fun serverId() = "${contractName()}-${id}"

    fun contractName() = collectionContractName ?: flowIdentifier?.split(".", ignoreCase = true, limit = 0)?.getOrNull(2) ?: ""

    fun tokenId() = id

    fun isNBA() = collectionName.trim().lowercase() == "TopShot".lowercase()

    fun canBridgeToFlow(): Boolean {
        return flowIdentifier.isNullOrBlank().not()
    }

    fun getNFTIdentifier(): String {
        return flowIdentifier ?: "A.${collectionAddress.removeAddressPrefix()}.${contractName()}.NFT"
    }

    fun canBridgeToEVM(): Boolean {
        val collection = NftCollectionConfig.get(collectionAddress, contractName()) ?: return false
        return collection.evmAddress.isNullOrEmpty().not()
    }
}

@Parcelize
data class PostMedia(
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("title")
    val title: String? = null,
    @SerializedName("video")
    val video: String? = null,
    @SerializedName("image")
    val image: String? = null,
    @SerializedName("music")
    val music: String? = null,
    @SerializedName("isSvg")
    val isSvg: String? = null,
) : Parcelable

@Parcelize
data class NftTraits(
    @SerializedName("name")
    val name: String,
    @SerializedName("value")
    val value: String,
) : Parcelable

@Parcelize
data class NFTContract(
    @SerializedName("address")
    val address: String,
    @SerializedName("contractMetadata")
    val contractMetadata: NFTContractMetadata?,
    @SerializedName("externalDomain")
    val externalDomain: String?,
    @SerializedName("name")
    val name: String?,
) : Parcelable

@Parcelize
data class NFTContractMetadata(
    @SerializedName("publicCollectionName")
    val publicCollectionName: String?,
    @SerializedName("publicPath")
    val publicPath: String?,
    @SerializedName("storagePath")
    val storagePath: String?,
) : Parcelable

@Parcelize
data class NFTMetadata(
    @SerializedName("metadata")
    val metadata: List<NFTMetadataX>? = null,
) : Parcelable

@Parcelize
data class NFTMetadataX(
    @SerializedName("name")
    val name: String,
    @SerializedName("value")
    val value: String
) : Parcelable

@Parcelize
data class NFTMedia(
    @SerializedName("mimeType")
    val mimeType: String,
    @SerializedName("uri")
    val uri: String
) : Parcelable

@Parcelize
data class NFTTokenMetadata(
    @SerializedName("uuid")
    val uuid: String
) : Parcelable
