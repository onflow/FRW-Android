package com.flowfoundation.wallet.manager.config

import android.os.Parcelable
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.cache.nftCollectionsCache
import com.flowfoundation.wallet.manager.app.isPreviewnet
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.manager.nft.NftCollectionStateManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.NftCollectionListResponse
import com.flowfoundation.wallet.network.model.PreviewnetNftCollectionListResponse
import com.flowfoundation.wallet.network.retrofitApi
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.readTextFromAssets
import com.flowfoundation.wallet.utils.svgToPng
import com.google.gson.reflect.TypeToken
import kotlinx.parcelize.Parcelize
import java.net.URL
import kotlin.math.log

object NftCollectionConfig {

    private val config = mutableListOf<NftCollection>()

    fun sync() {
        ioScope { reloadConfig() }
    }

    fun get(address: String?): NftCollection? {
        address ?: return null
        if (config.isEmpty()) {
            reloadConfig()
        }
        val list = config.toList()

        return list.firstOrNull { it.address == address }
    }

    fun getByContractName(contractName: String): NftCollection? {
        if (config.isEmpty()) {
            reloadConfig()
        }
        val list = config.toList()

        return list.firstOrNull { it.contractName == contractName }
    }

    fun getByStoragePath(storagePath: String): NftCollection? {
        if (config.isEmpty()) {
            reloadConfig()
        }
        val list = config.toList()
        return list.firstOrNull { it.path.storagePath == storagePath }
    }

    fun list() = config.toList()

    private fun reloadConfig() {
        ioScope {
            config.clear()
            config.addAll(loadFromCache())

            val response = if (isPreviewnet()) {
                val text = URL("https://raw.githubusercontent.com/Outblock/token-list-jsons/outblock/jsons/previewnet/flow/nfts.json").readText()
                val listResponse = Gson().fromJson(text, PreviewnetNftCollectionListResponse::class.java)
                NftCollectionListResponse(data = listResponse.tokens, status = 200)
            } else {
                retrofitApi().create(ApiService::class.java).nftCollections()
            }
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
            } else if (isPreviewnet()) {
                "config/nft_collections_previewnet.json"
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
    val address: String,
    @SerializedName("banner")
    val banner: String?,
    @SerializedName("contract_name")
    val contractName: String,
    @SerializedName("description")
    val description: String,
    @SerializedName("logo")
    val logo: String,
    @SerializedName("secure_cadence_compatible")
    val secureCadenceCompatible: CadenceCompatible?,
    @SerializedName("marketplace")
    val marketplace: String?,
    @SerializedName("name")
    val name: String,
    @SerializedName("official_website")
    val officialWebsite: String?,
    @SerializedName("path")
    val path: Path,
    @SerializedName("evmAddress")
    val evmAddress: String?,
) : Parcelable {

    fun logo(): String {
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
    ) : Parcelable

    @Parcelize
    data class CadenceCompatible(
        @SerializedName("mainnet")
        val mainnet: Boolean,
        @SerializedName("testnet")
        val testnet: Boolean,
    ) : Parcelable
}