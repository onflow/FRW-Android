package com.flowfoundation.wallet.network.model

import com.flowfoundation.wallet.utils.extensions.toSafeDecimal
import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.wallet.toAddress
import java.math.BigDecimal

data class InboxResponse(
    @SerializedName("id")
    val id: String,
    @SerializedName("owner")
    val owner: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("vaultBalances")
    val vaultBalances: Map<String, String>,
    @SerializedName("collections")
    val collections: Map<String, List<String>>,
) {
    fun tokenList(): List<InboxToken> {
        return vaultBalances.map {
            val data = it.key.split(".")
            InboxToken(
                key = it.key,
                coinAddress = data[1].toAddress(),
                coinSymbol = data[2],
                it.value.toSafeDecimal(),
            )
        }.filter { it.amount != BigDecimal.ZERO }
    }

    fun nftList(): List<InboxNft> {
        return collections.map { item ->
            val data = item.key.split(".")
            item.value.map {
                InboxNft(
                    key = item.key,
                    collectionAddress = data[1].toAddress(),
                    collectionName = data[2],
                    tokenId = it,
                )
            }
        }.flatten()
    }
}

data class InboxToken(
    val key: String,
    val coinAddress: String,
    val coinSymbol: String,
    val amount: BigDecimal,
    var marketValue: BigDecimal? = null,
)

data class InboxNft(
    val key: String,
    val collectionAddress: String,
    val collectionName: String,
    val tokenId: String,
)