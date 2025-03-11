package com.flowfoundation.wallet.manager.config

import android.os.Parcelable
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.cache.nftCollectionsCache
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.manager.nft.NftCollectionStateManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.NftCollectionListResponse
import com.flowfoundation.wallet.network.retrofitApi
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.readTextFromAssets
import com.flowfoundation.wallet.utils.svgToPng
import com.flowfoundation.wallet.wallet.removeAddressPrefix
import kotlinx.parcelize.Parcelize

object NftCollectionConfig {

    private val config = mutableListOf<NftCollection>()

    fun sync() {
        ioScope { reloadConfig() }
    }

    fun get(address: String? = null, contractName: String): NftCollection? {
        if (config.isEmpty()) {
            reloadConfig()
        }
        val list = config.toList()

        if (address == null) {
            return list.firstOrNull { it.contractName() == contractName }
        }
        return list.firstOrNull { it.address == address
                && it.contractName() == contractName }
    }

    fun getByNFTIdentifier(nftIdentifier: String): NftCollection? {
        if (config.isEmpty()) {
            reloadConfig()
        }
        return config.toList().firstOrNull { it.getNFTIdentifier() == nftIdentifier }
    }

    fun getByContractId(contractId: String): NftCollection? {
        if (config.isEmpty()) {
            reloadConfig()
        }
        return config.toList().firstOrNull { it.contractIdWithCollection() == contractId }
    }

    fun list() = config.toList()

    private fun reloadConfig() {
        ioScope {
            config.clear()
            config.addAll(loadFromCache())

            val response = retrofitApi().create(ApiService::class.java).getNFTCollections()
            if (response.data.isNotEmpty()) {
                config.clear()
                config.addAll(response.data)
                nftCollectionsCache().cache(response)
            }
            NftCollectionStateManager.fetchState()
        }
    }

    private fun loadFromCache(): List<NftCollection> {
        val cache = nftCollectionsCache()
        return cache.read()?.data ?: loadFromAssets()
    }


    private fun loadFromAssets(): List<NftCollection> {
        val text = readTextFromAssets(
            if (isTestnet()) {
                "config/nft_collections_testnet.json"
            } else {
                "config/nft_collections_mainnet.json"
            }
        )
        val data = Gson().fromJson(text, NftCollectionListResponse::class.java)
        return data.data
    }
}

@Parcelize
data class NftCollection(
    @SerializedName("id")
    val id: String,
    @SerializedName("address")
    val address: String?,
    @SerializedName("banner")
    val banner: String?,
    @SerializedName("contract_name")
    val contractName: String?,
    @SerializedName("description")
    val description: String?,
    @SerializedName("logo")
    val logo: String?,
    @SerializedName("secure_cadence_compatible")
    val secureCadenceCompatible: CadenceCompatible?,
    @SerializedName("marketplace")
    val marketplace: String?,
    @SerializedName("name")
    val name: String,
    @SerializedName("official_website")
    val officialWebsite: String?,
    @SerializedName("path")
    val path: Path?,
    @SerializedName("evmAddress")
    val evmAddress: String?,
    @SerializedName("flowIdentifier")
    val flowIdentifier: String?,
) : Parcelable {

    fun getNFTIdentifier(): String {
        return flowIdentifier ?: "A.${address?.removeAddressPrefix().orEmpty()}.${contractName}.NFT"
    }

    fun contractName(): String {
        return contractName ?: name
    }

    fun contractIdWithCollection() = "A.${address?.removeAddressPrefix().orEmpty()}.${contractName}.Collection"

    fun contractId() = "A.${address?.removeAddressPrefix().orEmpty()}.${contractName}"

    fun logo(): String {
        if (logo == null) {
            return ""
        }
        return if (logo.endsWith(".svg")) {
            logo.svgToPng()
        } else {
            logo
        }
    }

    fun banner(): String {
        if (banner == null) {
            return ""
        }
        return if (banner.endsWith(".svg")) {
            banner.svgToPng()
        } else {
            banner
        }
    }

    @Parcelize
    data class Address(
        @SerializedName("mainnet")
        val mainnet: String? = null,
        @SerializedName("testnet")
        val testnet: String? = null,
    ) : Parcelable

    @Parcelize
    data class Path(
        @SerializedName("public_collection_name")
        val publicCollectionName: String?,
        @SerializedName("public_path")
        val publicPath: String,
        @SerializedName("storage_path")
        val storagePath: String,
        @SerializedName("public_type")
        val publicType: String?,
        @SerializedName("private_type")
        val privateType: String?,
        @SerializedName("private_path")
        val privatePath: String?,
    ) : Parcelable

    @Parcelize
    data class CadenceCompatible(
        @SerializedName("mainnet")
        val mainnet: Boolean,
        @SerializedName("testnet")
        val testnet: Boolean,
    ) : Parcelable
}